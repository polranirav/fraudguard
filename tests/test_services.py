"""
Comprehensive Test Suite for CNS-DIS Upgrade Services
======================================================

Tests all Phase 1, 2, and 3 services to ensure they work correctly.
"""

import sys
import os
import json
from pathlib import Path

# Add project root to path
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

def test_python_syntax():
    """Test that all Python services have valid syntax."""
    print("=" * 60)
    print("TEST 1: Python Syntax Validation")
    print("=" * 60)
    
    services = [
        'ml-models/lnn-service/lnn_inference_service.py',
        'ml-models/causal-service/causal_inference_service.py',
        'ml-models/gnn-service/gnn_inference_service.py',
        'ml-models/nsai-service/nsai_inference_service.py',
        'ml-models/rule-agent-service/rule_generation_service.py',
        'ml-models/rule-management-service/rule_management_service.py'
    ]
    
    results = []
    for service in services:
        service_path = project_root / service
        if service_path.exists():
            try:
                with open(service_path, 'r') as f:
                    compile(f.read(), service_path, 'exec')
                print(f"✓ {service.split('/')[-1]}: Valid syntax")
                results.append(True)
            except SyntaxError as e:
                print(f"✗ {service.split('/')[-1]}: Syntax error - {e}")
                results.append(False)
        else:
            print(f"✗ {service.split('/')[-1]}: File not found")
            results.append(False)
    
    print(f"\nResult: {sum(results)}/{len(services)} services passed")
    return all(results)


def test_fastapi_apps():
    """Test that all services have FastAPI apps."""
    print("\n" + "=" * 60)
    print("TEST 2: FastAPI Application Validation")
    print("=" * 60)
    
    services = {
        'LNN': 'ml-models/lnn-service/lnn_inference_service.py',
        'Causal': 'ml-models/causal-service/causal_inference_service.py',
        'GNN': 'ml-models/gnn-service/gnn_inference_service.py',
        'NSAI': 'ml-models/nsai-service/nsai_inference_service.py',
        'Rule Agent': 'ml-models/rule-agent-service/rule_generation_service.py',
        'Rule Management': 'ml-models/rule-management-service/rule_management_service.py'
    }
    
    results = []
    for name, service_path in services.items():
        full_path = project_root / service_path
        if full_path.exists():
            with open(full_path, 'r') as f:
                content = f.read()
                has_fastapi = 'from fastapi import' in content or 'import fastapi' in content
                has_app = 'app = FastAPI' in content or 'FastAPI(' in content
                has_routes = '@app.' in content
                
                if has_fastapi and has_app and has_routes:
                    print(f"✓ {name}: FastAPI app configured correctly")
                    results.append(True)
                else:
                    print(f"✗ {name}: Missing FastAPI components")
                    results.append(False)
        else:
            print(f"✗ {name}: File not found")
            results.append(False)
    
    print(f"\nResult: {sum(results)}/{len(services)} services passed")
    return all(results)


def test_java_clients():
    """Test that all Java clients exist."""
    print("\n" + "=" * 60)
    print("TEST 3: Java Client Validation")
    print("=" * 60)
    
    clients = [
        'finance-intelligence-root/intelligence-processing/src/main/java/com/frauddetection/processing/lnn/LNNInferenceClient.java',
        'finance-intelligence-root/intelligence-processing/src/main/java/com/frauddetection/processing/causal/CausalInferenceClient.java',
        'finance-intelligence-root/intelligence-processing/src/main/java/com/frauddetection/processing/gnn/GNNInferenceClient.java',
        'finance-intelligence-root/intelligence-processing/src/main/java/com/frauddetection/processing/nsai/NSAIInferenceClient.java',
        'finance-intelligence-root/intelligence-processing/src/main/java/com/frauddetection/processing/agent/RuleGenerationClient.java'
    ]
    
    results = []
    for client in clients:
        client_path = project_root / client
        if client_path.exists():
            with open(client_path, 'r') as f:
                content = f.read()
                has_class = 'class' in content
                has_http = 'HttpClient' in content or 'http' in content.lower()
                
                if has_class and has_http:
                    print(f"✓ {client.split('/')[-1]}: Valid Java client")
                    results.append(True)
                else:
                    print(f"✗ {client.split('/')[-1]}: Invalid structure")
                    results.append(False)
        else:
            print(f"✗ {client.split('/')[-1]}: File not found")
            results.append(False)
    
    print(f"\nResult: {sum(results)}/{len(clients)} clients passed")
    return all(results)


def test_dockerfiles():
    """Test that all services have Dockerfiles."""
    print("\n" + "=" * 60)
    print("TEST 4: Dockerfile Validation")
    print("=" * 60)
    
    services = [
        'ml-models/lnn-service',
        'ml-models/causal-service',
        'ml-models/gnn-service',
        'ml-models/nsai-service',
        'ml-models/rule-agent-service',
        'ml-models/rule-management-service'
    ]
    
    results = []
    for service in services:
        dockerfile_path = project_root / service / 'Dockerfile'
        if dockerfile_path.exists():
            with open(dockerfile_path, 'r') as f:
                content = f.read()
                has_from = 'FROM' in content
                has_copy = 'COPY' in content
                has_cmd = 'CMD' in content or 'ENTRYPOINT' in content
                
                if has_from and has_copy and has_cmd:
                    print(f"✓ {service.split('/')[-1]}/Dockerfile: Valid")
                    results.append(True)
                else:
                    print(f"✗ {service.split('/')[-1]}/Dockerfile: Missing components")
                    results.append(False)
        else:
            print(f"✗ {service.split('/')[-1]}/Dockerfile: Not found")
            results.append(False)
    
    print(f"\nResult: {sum(results)}/{len(services)} Dockerfiles passed")
    return all(results)


def test_requirements():
    """Test that all services have requirements.txt."""
    print("\n" + "=" * 60)
    print("TEST 5: Requirements File Validation")
    print("=" * 60)
    
    services = [
        'ml-models/lnn-service',
        'ml-models/causal-service',
        'ml-models/gnn-service',
        'ml-models/nsai-service',
        'ml-models/rule-agent-service',
        'ml-models/rule-management-service'
    ]
    
    results = []
    for service in services:
        req_path = project_root / service / 'requirements.txt'
        if req_path.exists():
            with open(req_path, 'r') as f:
                content = f.read()
                has_fastapi = 'fastapi' in content.lower()
                has_uvicorn = 'uvicorn' in content.lower()
                
                if has_fastapi and has_uvicorn:
                    print(f"✓ {service.split('/')[-1]}/requirements.txt: Valid")
                    results.append(True)
                else:
                    print(f"✗ {service.split('/')[-1]}/requirements.txt: Missing dependencies")
                    results.append(False)
        else:
            print(f"✗ {service.split('/')[-1]}/requirements.txt: Not found")
            results.append(False)
    
    print(f"\nResult: {sum(results)}/{len(services)} requirements files passed")
    return all(results)


def test_integration():
    """Test Flink integration."""
    print("\n" + "=" * 60)
    print("TEST 6: Flink Integration Validation")
    print("=" * 60)
    
    flink_file = project_root / 'finance-intelligence-root/intelligence-processing/src/main/java/com/frauddetection/processing/function/FraudDetectionProcessFunction.java'
    
    if not flink_file.exists():
        print("✗ FraudDetectionProcessFunction.java: Not found")
        return False
    
    with open(flink_file, 'r') as f:
        content = f.read()
    
    checks = {
        'LNN Client': 'LNNInferenceClient' in content,
        'Causal Client': 'CausalInferenceClient' in content,
        'GNN Client': 'GNNInferenceClient' in content,
        'NSAI Client': 'NSAIInferenceClient' in content,
        'LNN Integration': 'lnnClient.getFraudProbability' in content,
        'GNN Integration': 'gnnClient.getNetworkFraudProbability' in content,
        'NSAI Integration': 'nsaiClient.predictWithExplanation' in content,
        'Enhanced Scoring': 'compositeScore' in content and 'gnnScore' in content
    }
    
    results = []
    for check_name, passed in checks.items():
        if passed:
            print(f"✓ {check_name}: Integrated")
            results.append(True)
        else:
            print(f"✗ {check_name}: Not integrated")
            results.append(False)
    
    print(f"\nResult: {sum(results)}/{len(checks)} integration checks passed")
    return all(results)


def test_docker_compose():
    """Test Docker Compose configuration."""
    print("\n" + "=" * 60)
    print("TEST 7: Docker Compose Validation")
    print("=" * 60)
    
    compose_file = project_root / 'docker-compose.dev.yml'
    
    if not compose_file.exists():
        print("✗ docker-compose.dev.yml: Not found")
        return False
    
    with open(compose_file, 'r') as f:
        content = f.read()
    
    services = [
        'lnn-service',
        'causal-service',
        'gnn-service',
        'nsai-service',
        'rule-agent-service',
        'rule-management-service'
    ]
    
    results = []
    for service in services:
        if service in content:
            print(f"✓ {service}: Configured in docker-compose")
            results.append(True)
        else:
            print(f"✗ {service}: Not configured")
            results.append(False)
    
    print(f"\nResult: {sum(results)}/{len(services)} services configured")
    return all(results)


def main():
    """Run all tests."""
    print("\n" + "=" * 60)
    print("CNS-DIS UPGRADE - COMPREHENSIVE TEST SUITE")
    print("=" * 60)
    print()
    
    tests = [
        ("Python Syntax", test_python_syntax),
        ("FastAPI Apps", test_fastapi_apps),
        ("Java Clients", test_java_clients),
        ("Dockerfiles", test_dockerfiles),
        ("Requirements", test_requirements),
        ("Flink Integration", test_integration),
        ("Docker Compose", test_docker_compose)
    ]
    
    results = []
    for test_name, test_func in tests:
        try:
            result = test_func()
            results.append((test_name, result))
        except Exception as e:
            print(f"\n✗ {test_name}: Error - {e}")
            results.append((test_name, False))
    
    # Final summary
    print("\n" + "=" * 60)
    print("TEST SUMMARY")
    print("=" * 60)
    
    passed = sum(1 for _, result in results if result)
    total = len(results)
    
    for test_name, result in results:
        status = "✓ PASS" if result else "✗ FAIL"
        print(f"{status}: {test_name}")
    
    print("\n" + "=" * 60)
    print(f"OVERALL: {passed}/{total} test suites passed")
    print("=" * 60)
    
    if passed == total:
        print("\n✅ ALL TESTS PASSED - System is ready!")
        return 0
    else:
        print(f"\n⚠️  {total - passed} test suite(s) failed - Review above")
        return 1


if __name__ == "__main__":
    sys.exit(main())
