# Branch Structure - FraudGuard Repository

## Overview

The repository now has two main branches:

1. **`main`** - Original FraudGuard system (stable, production-ready)
2. **`cns-dis-upgrade`** - CNS-DIS upgrade with all three phases (experimental/upgrade branch)

---

## Branch Details

### `main` Branch

**Purpose**: Original FraudGuard fraud detection system

**Status**: ✅ Stable, Production-Ready

**Features**:
- Rule-based fraud detection
- ML (XGBoost) inference
- Apache Flink stream processing
- Kafka event streaming
- Redis enrichment
- Azure Synapse Analytics integration

**Last Commit**: `6fb3440` - "docs: Update all documentation to reflect actual project structure"

**GitHub URL**: `https://github.com/polranirav/fraudguard/tree/main`

---

### `cns-dis-upgrade` Branch

**Purpose**: Cognitive Neuro-Symbolic Digital Immune System (CNS-DIS) upgrade

**Status**: ✅ Complete, Ready for Testing

**Features** (in addition to main branch):
- **Phase 1**: LNN (Liquid Neural Networks) + Causal Inference
- **Phase 2**: GNN (Graph Neural Networks) + NSAI (Neuro-Symbolic AI)
- **Phase 3**: Autonomous Rule Generation Agents

**Commits**:
- `0b91524` - Add comprehensive validation report for CNS-DIS upgrade
- `0c3ec5f` - Implement Phase 3: Autonomous Rule Generation Agents
- `3cfc965` - feat: Add CNS-DIS upgrade research and architecture

**GitHub URL**: `https://github.com/polranirav/fraudguard/tree/cns-dis-upgrade`

**Pull Request**: Create PR at `https://github.com/polranirav/fraudguard/pull/new/cns-dis-upgrade`

---

## Usage

### Working with Main Branch

```bash
# Checkout main branch
git checkout main

# Pull latest changes
git pull origin main

# This is the stable, production-ready version
```

### Working with Upgrade Branch

```bash
# Checkout upgrade branch
git checkout cns-dis-upgrade

# Pull latest changes
git pull origin cns-dis-upgrade

# This branch contains all CNS-DIS upgrade features
```

### Merging Upgrade to Main (Future)

When the upgrade is tested and validated:

```bash
# Switch to main
git checkout main

# Merge upgrade branch
git merge cns-dis-upgrade

# Push to remote
git push origin main
```

---

## Branch Comparison

| Feature | main Branch | cns-dis-upgrade Branch |
|---------|-------------|------------------------|
| Rule-Based Detection | ✅ | ✅ |
| ML (XGBoost) | ✅ | ✅ |
| Apache Flink | ✅ | ✅ |
| Kafka | ✅ | ✅ |
| Redis | ✅ | ✅ |
| LNN Service | ❌ | ✅ |
| Causal Inference | ❌ | ✅ |
| GNN Service | ❌ | ✅ |
| NSAI Service | ❌ | ✅ |
| Rule Generation Agents | ❌ | ✅ |
| Rule Management | ❌ | ✅ |

---

## Services Comparison

### Main Branch Services
- ML Inference Service (Port 8000)

### Upgrade Branch Services
- ML Inference Service (Port 8000)
- LNN Service (Port 8001) - Phase 1
- Causal Service (Port 8002) - Phase 1
- GNN Service (Port 8003) - Phase 2
- NSAI Service (Port 8004) - Phase 2
- Rule Agent Service (Port 8005) - Phase 3
- Rule Management Service (Port 8006) - Phase 3

---

## Documentation

### Main Branch
- `README.md` - Original system documentation
- `docs/ARCHITECTURE.md` - Original architecture
- `docs/FRAUD_DETECTION_RULES.md` - Detection rules
- `IMPLEMENTATION_GUIDE.md` - Implementation guide

### Upgrade Branch
- All main branch documentation
- `docs/PHASE1_IMPLEMENTATION.md` - Phase 1 guide
- `docs/PHASE2_IMPLEMENTATION.md` - Phase 2 guide
- `docs/PHASE3_IMPLEMENTATION.md` - Phase 3 guide
- `docs/CNS_DIS_ARCHITECTURE.md` - CNS-DIS architecture
- `docs/UPGRADE_RESEARCH_CNS_DIS.md` - Research and feasibility
- `VALIDATION_REPORT.md` - Validation report

---

## Next Steps

1. **Test Upgrade Branch**: Deploy and test CNS-DIS upgrade in staging
2. **Validate Performance**: Ensure 50K+ TPS maintained
3. **Collect Feedback**: Get analyst and stakeholder feedback
4. **Merge Decision**: Decide when to merge upgrade to main

---

**Last Updated**: 2026-01-04  
**Main Branch**: Stable, Production-Ready  
**Upgrade Branch**: Complete, Ready for Testing
