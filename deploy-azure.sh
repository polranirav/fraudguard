#!/bin/bash
#===============================================================================
# Azure Infrastructure Deployment Script
#
# Deploys the complete Fraud Detection Platform infrastructure to Azure.
#
# Prerequisites:
# - Azure CLI installed and logged in
# - Subscription with appropriate permissions
#
# Usage: ./deploy-azure.sh [environment] [location]
#   Examples:
#     ./deploy-azure.sh dev eastus
#     ./deploy-azure.sh prod westus2
#===============================================================================

set -euo pipefail

# Configuration
ENVIRONMENT="${1:-dev}"
LOCATION="${2:-eastus}"
PROJECT_NAME="fraud"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BICEP_DIR="${SCRIPT_DIR}/infrastructure/azure"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }

echo "=========================================="
echo "Fraud Detection Platform - Azure Deploy"
echo "=========================================="
echo ""
echo "Environment: $ENVIRONMENT"
echo "Location: $LOCATION"
echo ""

# =============================================================================
# Step 1: Verify Azure CLI Login
# =============================================================================
log_step "Verifying Azure CLI login..."
if ! az account show &> /dev/null; then
    log_error "Not logged into Azure. Please run: az login"
    exit 1
fi

SUBSCRIPTION_ID=$(az account show --query id -o tsv)
SUBSCRIPTION_NAME=$(az account show --query name -o tsv)
log_info "Using subscription: $SUBSCRIPTION_NAME ($SUBSCRIPTION_ID)"

# =============================================================================
# Step 2: Generate Synapse Password
# =============================================================================
log_step "Generating secure Synapse admin password..."
SYNAPSE_PASSWORD=$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9!@#$%' | head -c 20)
log_info "Password generated (will be stored in Key Vault)"

# =============================================================================
# Step 3: Deploy Bicep Template
# =============================================================================
log_step "Deploying Azure infrastructure..."
DEPLOYMENT_NAME="fraud-${ENVIRONMENT}-$(date +%Y%m%d-%H%M%S)"

az deployment sub create \
    --name "$DEPLOYMENT_NAME" \
    --location "$LOCATION" \
    --template-file "${BICEP_DIR}/main.bicep" \
    --parameters \
        environment="$ENVIRONMENT" \
        location="$LOCATION" \
        projectName="$PROJECT_NAME" \
        synapseAdminPassword="$SYNAPSE_PASSWORD"

if [ $? -ne 0 ]; then
    log_error "Deployment failed!"
    exit 1
fi

log_info "Infrastructure deployment complete!"

# =============================================================================
# Step 4: Get Deployment Outputs
# =============================================================================
log_step "Retrieving deployment outputs..."
RESOURCE_GROUP=$(az deployment sub show --name "$DEPLOYMENT_NAME" --query 'properties.outputs.resourceGroupName.value' -o tsv)
AKS_NAME=$(az deployment sub show --name "$DEPLOYMENT_NAME" --query 'properties.outputs.aksClusterName.value' -o tsv)
ACR_SERVER=$(az deployment sub show --name "$DEPLOYMENT_NAME" --query 'properties.outputs.acrLoginServer.value' -o tsv)
SYNAPSE_ENDPOINT=$(az deployment sub show --name "$DEPLOYMENT_NAME" --query 'properties.outputs.synapseEndpoint.value' -o tsv)
STORAGE_ACCOUNT=$(az deployment sub show --name "$DEPLOYMENT_NAME" --query 'properties.outputs.storageAccountName.value' -o tsv)

echo ""
echo "=========================================="
echo "Deployment Summary"
echo "=========================================="
echo "Resource Group:    $RESOURCE_GROUP"
echo "AKS Cluster:       $AKS_NAME"
echo "ACR Server:        $ACR_SERVER"
echo "Synapse Endpoint:  $SYNAPSE_ENDPOINT"
echo "Storage Account:   $STORAGE_ACCOUNT"
echo ""

# =============================================================================
# Step 5: Get AKS Credentials
# =============================================================================
log_step "Configuring kubectl for AKS..."
az aks get-credentials \
    --resource-group "$RESOURCE_GROUP" \
    --name "$AKS_NAME" \
    --overwrite-existing

log_info "kubectl configured for cluster: $AKS_NAME"

# =============================================================================
# Step 6: Install Flink Kubernetes Operator
# =============================================================================
log_step "Installing Flink Kubernetes Operator..."

# Add Flink Operator Helm repo
helm repo add flink-kubernetes-operator https://downloads.apache.org/flink/flink-kubernetes-operator-1.7.0/
helm repo update

# Create namespace
kubectl create namespace flink-fraud --dry-run=client -o yaml | kubectl apply -f -

# Install Flink Operator
helm upgrade --install flink-kubernetes-operator flink-kubernetes-operator/flink-kubernetes-operator \
    --namespace flink-fraud \
    --set webhook.create=true \
    --wait

log_info "Flink Kubernetes Operator installed!"

# =============================================================================
# Step 7: Login to ACR
# =============================================================================
log_step "Logging into Azure Container Registry..."
az acr login --name "${ACR_SERVER%%.*}"

# =============================================================================
# Complete
# =============================================================================
echo ""
echo "=========================================="
echo "Deployment Complete!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "  1. Build and push Docker image:"
echo "     docker build -t ${ACR_SERVER}/flink-fraud:latest -f infrastructure/docker/Dockerfile.flink ."
echo "     docker push ${ACR_SERVER}/flink-fraud:latest"
echo ""
echo "  2. Update Flink deployment with ACR:"
echo "     sed -i 's|acr-fraud.azurecr.io|${ACR_SERVER}|g' infrastructure/k8s/flink-deployment.yaml"
echo ""
echo "  3. Deploy Flink job:"
echo "     kubectl apply -f infrastructure/k8s/flink-deployment.yaml"
echo ""
echo "  4. Access Flink Dashboard:"
echo "     kubectl port-forward svc/fraud-detection-cluster-rest 8081:8081 -n flink-fraud"
echo "     Open: http://localhost:8081"
echo ""
