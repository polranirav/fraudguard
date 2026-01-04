// =============================================================================
// Azure Bicep - Main Infrastructure Template
// 
// Provisions all Azure resources for the Fraud Detection Platform:
// - Resource Group
// - Azure Kubernetes Service (AKS)
// - Azure Synapse Analytics
// - Azure Data Lake Storage Gen2
// - Azure Container Registry
// - Redis Cache
// - Key Vault
// =============================================================================

targetScope = 'subscription'

// Parameters
@description('Environment name (dev, staging, prod)')
@allowed(['dev', 'staging', 'prod'])
param environment string = 'dev'

@description('Azure region for resources')
param location string = 'eastus'

@description('Project name prefix')
param projectName string = 'fraud'

@description('AKS node count')
@minValue(1)
@maxValue(10)
param aksNodeCount int = 3

@description('AKS node VM size')
param aksVmSize string = 'Standard_D4s_v3'

@description('Synapse SQL pool SKU')
param synapseSku string = 'DW100c'

@description('Administrator username for Synapse')
param synapseAdminUser string = 'sqladmin'

@secure()
@description('Administrator password for Synapse')
param synapseAdminPassword string

// Variables
var resourceGroupName = 'rg-${projectName}-${environment}'
var aksClusterName = 'aks-${projectName}-${environment}'
var synapseWorkspaceName = 'synapse-${projectName}-${environment}'
var storageAccountName = 'adls${projectName}${environment}'
var acrName = 'acr${projectName}${environment}'
var redisName = 'redis-${projectName}-${environment}'
var keyVaultName = 'kv-${projectName}-${environment}'
var logAnalyticsName = 'law-${projectName}-${environment}'

// Resource Group
resource rg 'Microsoft.Resources/resourceGroups@2021-04-01' = {
  name: resourceGroupName
  location: location
  tags: {
    environment: environment
    project: projectName
    createdBy: 'bicep'
  }
}

// Deploy all resources in the resource group
module resources 'modules/all-resources.bicep' = {
  name: 'deploy-all-resources'
  scope: rg
  params: {
    location: location
    environment: environment
    projectName: projectName
    aksClusterName: aksClusterName
    aksNodeCount: aksNodeCount
    aksVmSize: aksVmSize
    synapseWorkspaceName: synapseWorkspaceName
    synapseSku: synapseSku
    synapseAdminUser: synapseAdminUser
    synapseAdminPassword: synapseAdminPassword
    storageAccountName: storageAccountName
    acrName: acrName
    redisName: redisName
    keyVaultName: keyVaultName
    logAnalyticsName: logAnalyticsName
  }
}

// Outputs
output resourceGroupName string = rg.name
output aksClusterName string = resources.outputs.aksClusterName
output acrLoginServer string = resources.outputs.acrLoginServer
output synapseEndpoint string = resources.outputs.synapseEndpoint
output storageAccountName string = resources.outputs.storageAccountName
output redisHostName string = resources.outputs.redisHostName
