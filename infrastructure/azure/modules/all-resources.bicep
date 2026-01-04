// =============================================================================
// Azure Bicep - All Resources Module
// =============================================================================

// Parameters
param location string
param environment string
param projectName string
param aksClusterName string
param aksNodeCount int
param aksVmSize string
param synapseWorkspaceName string
param synapseSku string
param synapseAdminUser string
@secure()
param synapseAdminPassword string
param storageAccountName string
param acrName string
param redisName string
param keyVaultName string
param logAnalyticsName string

// =============================================================================
// Log Analytics Workspace (for monitoring)
// =============================================================================
resource logAnalytics 'Microsoft.OperationalInsights/workspaces@2022-10-01' = {
  name: logAnalyticsName
  location: location
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: 30
  }
}

// =============================================================================
// Azure Data Lake Storage Gen2
// =============================================================================
resource storageAccount 'Microsoft.Storage/storageAccounts@2023-01-01' = {
  name: storageAccountName
  location: location
  sku: {
    name: 'Standard_LRS'
  }
  kind: 'StorageV2'
  properties: {
    isHnsEnabled: true  // Enable hierarchical namespace for ADLS Gen2
    accessTier: 'Hot'
    supportsHttpsTrafficOnly: true
    minimumTlsVersion: 'TLS1_2'
    networkAcls: {
      defaultAction: 'Deny'
      bypass: 'AzureServices'
    }
  }
}

// ADLS Containers
resource rawContainer 'Microsoft.Storage/storageAccounts/blobServices/containers@2023-01-01' = {
  name: '${storageAccount.name}/default/raw'
  properties: {
    publicAccess: 'None'
  }
}

resource curatedContainer 'Microsoft.Storage/storageAccounts/blobServices/containers@2023-01-01' = {
  name: '${storageAccount.name}/default/curated'
  properties: {
    publicAccess: 'None'
  }
}

resource checkpointsContainer 'Microsoft.Storage/storageAccounts/blobServices/containers@2023-01-01' = {
  name: '${storageAccount.name}/default/checkpoints'
  properties: {
    publicAccess: 'None'
  }
}

// =============================================================================
// Azure Container Registry
// =============================================================================
resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: acrName
  location: location
  sku: {
    name: 'Standard'
  }
  properties: {
    adminUserEnabled: false
  }
}

// =============================================================================
// Azure Kubernetes Service
// =============================================================================
resource aks 'Microsoft.ContainerService/managedClusters@2023-09-01' = {
  name: aksClusterName
  location: location
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    dnsPrefix: aksClusterName
    kubernetesVersion: '1.28'
    enableRBAC: true
    agentPoolProfiles: [
      {
        name: 'nodepool1'
        count: aksNodeCount
        vmSize: aksVmSize
        osType: 'Linux'
        osSKU: 'Ubuntu'
        mode: 'System'
        availabilityZones: ['1', '2', '3']
        enableAutoScaling: true
        minCount: 2
        maxCount: 10
      }
      {
        name: 'flinkpool'
        count: 3
        vmSize: 'Standard_D8s_v3'
        osType: 'Linux'
        mode: 'User'
        nodeLabels: {
          'workload': 'flink'
        }
        nodeTaints: ['flink=true:NoSchedule']
        enableAutoScaling: true
        minCount: 2
        maxCount: 20
      }
    ]
    networkProfile: {
      networkPlugin: 'azure'
      networkPolicy: 'calico'
      serviceCidr: '10.0.0.0/16'
      dnsServiceIP: '10.0.0.10'
    }
    addonProfiles: {
      omsagent: {
        enabled: true
        config: {
          logAnalyticsWorkspaceResourceID: logAnalytics.id
        }
      }
    }
    oidcIssuerProfile: {
      enabled: true
    }
    securityProfile: {
      workloadIdentity: {
        enabled: true
      }
    }
  }
}

// Grant AKS access to ACR
resource acrPullRole 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(aks.id, acr.id, 'acrpull')
  scope: acr
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')
    principalId: aks.properties.identityProfile.kubeletidentity.objectId
    principalType: 'ServicePrincipal'
  }
}

// =============================================================================
// Azure Synapse Analytics
// =============================================================================
resource synapse 'Microsoft.Synapse/workspaces@2021-06-01' = {
  name: synapseWorkspaceName
  location: location
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    defaultDataLakeStorage: {
      accountUrl: 'https://${storageAccount.name}.dfs.core.windows.net'
      filesystem: 'synapse'
    }
    sqlAdministratorLogin: synapseAdminUser
    sqlAdministratorLoginPassword: synapseAdminPassword
    managedVirtualNetwork: 'default'
    managedResourceGroupName: 'mrg-${synapseWorkspaceName}'
  }
}

// Synapse SQL Pool (Dedicated)
resource synapseSqlPool 'Microsoft.Synapse/workspaces/sqlPools@2021-06-01' = {
  parent: synapse
  name: 'fraudpool'
  location: location
  sku: {
    name: synapseSku
  }
  properties: {
    collation: 'SQL_Latin1_General_CP1_CI_AS'
  }
}

// Synapse Firewall - Allow Azure Services
resource synapseFirewall 'Microsoft.Synapse/workspaces/firewallRules@2021-06-01' = {
  parent: synapse
  name: 'AllowAllAzureIps'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

// =============================================================================
// Azure Redis Cache
// =============================================================================
resource redis 'Microsoft.Cache/redis@2023-08-01' = {
  name: redisName
  location: location
  properties: {
    sku: {
      name: 'Standard'
      family: 'C'
      capacity: 1
    }
    enableNonSslPort: false
    minimumTlsVersion: '1.2'
    redisConfiguration: {
      'maxmemory-policy': 'allkeys-lru'
    }
  }
}

// =============================================================================
// Azure Key Vault
// =============================================================================
resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: keyVaultName
  location: location
  properties: {
    sku: {
      family: 'A'
      name: 'standard'
    }
    tenantId: subscription().tenantId
    enableRbacAuthorization: true
    enableSoftDelete: true
    softDeleteRetentionInDays: 90
    networkAcls: {
      defaultAction: 'Deny'
      bypass: 'AzureServices'
    }
  }
}

// Store Redis connection string in Key Vault
resource redisConnectionSecret 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: keyVault
  name: 'redis-connection-string'
  properties: {
    value: '${redis.properties.hostName}:6380,password=${redis.listKeys().primaryKey},ssl=True,abortConnect=False'
  }
}

// =============================================================================
// Outputs
// =============================================================================
output aksClusterName string = aks.name
output acrLoginServer string = acr.properties.loginServer
output synapseEndpoint string = synapse.properties.connectivityEndpoints.sql
output storageAccountName string = storageAccount.name
output redisHostName string = redis.properties.hostName
output keyVaultUri string = keyVault.properties.vaultUri
