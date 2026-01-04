#!/bin/bash
#===============================================================================
# Development Environment Setup Script
#
# This script installs all prerequisites for the Fraud Detection Platform.
# Run with: chmod +x setup-dev-env.sh && ./setup-dev-env.sh
#===============================================================================

set -e

echo "=========================================="
echo "Fraud Detection Platform - Dev Env Setup"
echo "=========================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Check OS
if [[ "$OSTYPE" != "darwin"* ]]; then
    log_error "This script is designed for macOS. Please install manually on other systems."
    exit 1
fi

# =============================================================================
# Step 1: Install Homebrew (if not installed)
# =============================================================================
if ! command -v brew &> /dev/null; then
    log_info "Installing Homebrew..."
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    
    # Add Homebrew to PATH
    echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
    eval "$(/opt/homebrew/bin/brew shellenv)"
else
    log_info "Homebrew already installed: $(brew --version | head -1)"
fi

# =============================================================================
# Step 2: Install Java 17 (required for Flink compatibility)
# =============================================================================
if ! java --version 2>&1 | grep -q "17"; then
    log_info "Installing Java 17 (Temurin)..."
    brew install --cask temurin@17
    
    # Set JAVA_HOME
    export JAVA_HOME=$(/usr/libexec/java_home -v 17)
    echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc
else
    log_info "Java 17 already available"
fi

# =============================================================================
# Step 3: Install Maven
# =============================================================================
if ! command -v mvn &> /dev/null; then
    log_info "Installing Maven..."
    brew install maven
else
    log_info "Maven already installed: $(mvn --version | head -1)"
fi

# =============================================================================
# Step 4: Install Docker Desktop
# =============================================================================
if ! command -v docker &> /dev/null; then
    log_info "Installing Docker Desktop..."
    brew install --cask docker
    log_warn "Please open Docker Desktop from Applications and complete setup"
    log_warn "Then run this script again to continue"
else
    log_info "Docker already installed: $(docker --version)"
fi

# =============================================================================
# Step 5: Install kubectl
# =============================================================================
if ! command -v kubectl &> /dev/null; then
    log_info "Installing kubectl..."
    brew install kubectl
else
    log_info "kubectl already installed: $(kubectl version --client --short 2>/dev/null)"
fi

# =============================================================================
# Step 6: Install Azure CLI
# =============================================================================
if ! command -v az &> /dev/null; then
    log_info "Installing Azure CLI..."
    brew install azure-cli
else
    log_info "Azure CLI already installed: $(az version --query '"azure-cli"' -o tsv)"
fi

# =============================================================================
# Step 7: Install Helm (for Flink Operator)
# =============================================================================
if ! command -v helm &> /dev/null; then
    log_info "Installing Helm..."
    brew install helm
else
    log_info "Helm already installed: $(helm version --short)"
fi

# =============================================================================
# Verification
# =============================================================================
echo ""
echo "=========================================="
echo "Installation Summary"
echo "=========================================="
echo ""

check_tool() {
    if command -v $1 &> /dev/null; then
        echo -e "${GREEN}✓${NC} $1: $($1 --version 2>&1 | head -1)"
    else
        echo -e "${RED}✗${NC} $1: Not installed"
    fi
}

check_tool brew
check_tool java
check_tool mvn
check_tool docker
check_tool kubectl
check_tool az
check_tool helm

echo ""
log_info "Setup complete! Next steps:"
echo "  1. Open Docker Desktop and start the Docker daemon"
echo "  2. Run: cd finance-intelligence-root && mvn clean package"
echo "  3. Run: docker-compose -f docker-compose.dev.yml up"
echo ""
