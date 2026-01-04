"""
Integration Tests for CNS-DIS Services
======================================

Tests service integration and data flow.
"""

import json
import sys
from pathlib import Path

project_root = Path(__file__).parent.parent

def test_service_ports():
    """Test that all services have unique ports."""
    print("=" * 60)
    print("TEST: Service Port Configuration")
    print("=" * 60)
    
    expected_ports = {
        'lnn-service': 8001,
        'causal-service': 8002,
        'gnn-service': 8003,
        'nsai-service': 8004,
        'rule-agent-service': 8005,
        'rule-management-service': 8006
    }
    
    compose_file = project_root / 'docker-compose.dev.yml'
    if not compose_file.exists():
        print("✗ docker-compose.dev.yml not found")
        return False
    
    with open(compose_file, 'r') as f:
        content = f.read()
    
    results = []
    for service, port in expected_ports.items():
        if f'"{port}:' in content or f'- "{port}:' in content:
            print(f"✓ {service}: Port {port} configured")
            results.append(True)
        else:
            print(f"✗ {service}: Port {port} not found")
            results.append(False)
    
    print(f"\nResult: {sum(results)}/{len(expected_ports)} ports configured")
    return all(results)


def test_flink_integration():
    """Test Flink ProcessFunction integration."""
    print("\n" + "=" * 60)
    print("TEST: Flink Integration")
    print("=" * 60)
    
    flink_file = project_root / 'finance-intelligence-root/intelligence-processing/src/main/java/com/frauddetection/processing/function/FraudDetectionProcessFunction.java'
    
    if not flink_file.exists():
        print("✗ FraudDetectionProcessFunction.java not found")
        return False
    
    with open(flink_file, 'r') as f:
        content = f.read()
    
    checks = {
        'LNN Client Import': 'LNNInferenceClient' in content and 'import' in content,
        'Causal Client Import': 'CausalInferenceClient' in content and 'import' in content,
        'GNN Client Import': 'GNNInferenceClient' in content and 'import' in content,
        'NSAI Client Import': 'NSAIInferenceClient' in content and 'import' in content,
        'LNN Client Initialized': 'lnnClient = new LNNInferenceClient' in content,
        'GNN Client Initialized': 'gnnClient = new GNNInferenceClient' in content,
        'NSAI Client Initialized': 'nsaiClient = new NSAIInferenceClient' in content,
        'LNN Score Used': 'lnnScore' in content,
        'GNN Score Used': 'gnnScore' in content,
        'NSAI Score Used': 'nsaiScore' in content,
        'Enhanced Composite Score': 'compositeScore' in content and 'gnnScore' in content and 'nsaiScore' in content
    }
    
    results = []
    for check_name, passed in checks.items():
        if passed:
            print(f"✓ {check_name}")
            results.append(True)
        else:
            print(f"✗ {check_name}")
            results.append(False)
    
    print(f"\nResult: {sum(results)}/{len(checks)} checks passed")
    return all(results)


def test_service_structure():
    """Test that all services have required files."""
    print("\n" + "=" * 60)
    print("TEST: Service File Structure")
    print("=" * 60)
    
    services = {
        'LNN': 'ml-models/lnn-service',
        'Causal': 'ml-models/causal-service',
        'GNN': 'ml-models/gnn-service',
        'NSAI': 'ml-models/nsai-service',
        'Rule Agent': 'ml-models/rule-agent-service',
        'Rule Management': 'ml-models/rule-management-service'
    }
    
    results = []
    for name, service_dir in services.items():
        service_path = project_root / service_dir
        required_files = [
            '*_service.py',
            'Dockerfile',
            'requirements.txt',
            'k8s-deployment.yaml'
        ]
        
        all_exist = True
        for pattern in required_files:
            import glob
            matches = glob.glob(str(service_path / pattern))
            if not matches:
                all_exist = False
                break
        
        if all_exist:
            print(f"✓ {name}: All required files present")
            results.append(True)
        else:
            print(f"✗ {name}: Missing required files")
            results.append(False)
    
    print(f"\nResult: {sum(results)}/{len(services)} services complete")
    return all(results)


def main():
    """Run integration tests."""
    print("\n" + "=" * 60)
    print("CNS-DIS UPGRADE - INTEGRATION TESTS")
    print("=" * 60)
    print()
    
    tests = [
        ("Service Ports", test_service_ports),
        ("Flink Integration", test_flink_integration),
        ("Service Structure", test_service_structure)
    ]
    
    results = []
    for test_name, test_func in tests:
        try:
            result = test_func()
            results.append((test_name, result))
        except Exception as e:
            print(f"\n✗ {test_name}: Error - {e}")
            results.append((test_name, False))
    
    # Summary
    print("\n" + "=" * 60)
    print("INTEGRATION TEST SUMMARY")
    print("=" * 60)
    
    passed = sum(1 for _, result in results if result)
    total = len(results)
    
    for test_name, result in results:
        status = "✓ PASS" if result else "✗ FAIL"
        print(f"{status}: {test_name}")
    
    print(f"\nOVERALL: {passed}/{total} integration tests passed")
    
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
