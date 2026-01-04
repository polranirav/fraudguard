# Contributing Guidelines

Thank you for your interest in contributing to the Real-Time Fraud Detection Platform!

---

## 🚀 Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose
- Python 3.9+ (for ML components)
- Git

### Development Setup

1. **Fork and Clone**
   ```bash
   git clone <your-fork-url>
   cd Real-Time-Financial-Fraud-Detection-Platform
   ```

2. **Build the Project**
   ```bash
   cd fraud-detection-core
   mvn clean install
   ```

3. **Start Local Services**
   ```bash
   docker-compose -f docker-compose.dev.yml up -d
   ```

4. **Run Tests**
   ```bash
   mvn test
   ```

---

## 📝 Development Workflow

### 1. Create a Feature Branch

```bash
git checkout -b feature/your-feature-name
```

**Branch Naming Convention**:
- `feature/` - New features
- `fix/` - Bug fixes
- `docs/` - Documentation updates
- `refactor/` - Code refactoring
- `test/` - Test additions/updates

### 2. Make Your Changes

- Write clean, readable code
- Follow existing code style
- Add comments for complex logic
- Update documentation if needed

### 3. Write Tests

- Add unit tests for new functionality
- Ensure all tests pass: `mvn test`
- Aim for >80% code coverage

### 4. Commit Your Changes

```bash
git add .
git commit -m "feat: add new fraud detection rule"
```

**Commit Message Format**:
- `feat:` - New feature
- `fix:` - Bug fix
- `docs:` - Documentation
- `refactor:` - Code refactoring
- `test:` - Tests
- `chore:` - Build/config changes

### 5. Push and Create Pull Request

```bash
git push origin feature/your-feature-name
```

Then create a Pull Request on GitHub.

---

## 🏗️ Code Structure

### Module Organization

```
fraud-detection-core/
├── fraud-common/          # Shared models, DTOs, utilities
├── fraud-ingestion/       # Kafka producers
├── fraud-processing/      # Flink jobs (main logic)
├── fraud-enrichment/      # Redis enrichment
├── fraud-persistence/     # Data warehouse sinks
└── fraud-alerts/          # Alert service
```

### Code Style

- **Java**: Follow Google Java Style Guide
- **Naming**: Use descriptive names, avoid abbreviations
- **Comments**: Document "why", not "what"
- **Formatting**: Use IDE formatter (IntelliJ/Eclipse)

### Example

```java
/**
 * Detects velocity violations (rapid transaction succession).
 * 
 * This method checks if a customer has exceeded transaction
 * frequency or amount thresholds within a time window.
 * 
 * @param transaction The current transaction
 * @param timeWindowMs Time window in milliseconds
 * @return VelocityResult with violation status and risk score
 */
public VelocityResult checkVelocity(Transaction transaction, long timeWindowMs) {
    // Implementation
}
```

---

## 🧪 Testing Guidelines

### Unit Tests

- Test each method independently
- Use descriptive test names: `testVelocityCheck_ExceedsThreshold_ReturnsHighRisk()`
- Mock external dependencies (Kafka, Redis, etc.)

### Integration Tests

- Test component interactions
- Use test containers for Kafka/Redis
- Verify end-to-end flows

### Example Test

```java
@Test
public void testVelocityCheck_ExceedsThreshold_ReturnsHighRisk() {
    // Given
    Transaction txn = createTransaction(amount: 3000);
    addRecentTransactions(6); // Exceeds count threshold
    
    // When
    VelocityResult result = fraudDetector.checkVelocity(txn, 60000);
    
    // Then
    assertTrue(result.isViolation());
    assertTrue(result.getRiskScore() > 0.7);
}
```

---

## 📚 Documentation

### Code Documentation

- Document all public classes and methods
- Include parameter descriptions
- Explain complex algorithms

### README Updates

- Update README.md if adding new features
- Add examples for new functionality
- Update configuration sections

### Architecture Documentation

- Update `docs/ARCHITECTURE.md` for architectural changes
- Add diagrams for new components
- Document design decisions

---

## 🔍 Code Review Process

### Before Submitting PR

- [ ] Code compiles without errors
- [ ] All tests pass
- [ ] Code follows style guidelines
- [ ] Documentation updated
- [ ] No hardcoded credentials
- [ ] Logging added for debugging

### Review Checklist

Reviewers will check:
- Code quality and readability
- Test coverage
- Performance implications
- Security considerations
- Documentation completeness

---

## 🐛 Bug Reports

### Reporting Bugs

1. Check if bug already exists in issues
2. Create new issue with:
   - Clear description
   - Steps to reproduce
   - Expected vs. actual behavior
   - Environment details
   - Logs/error messages

### Bug Fix Process

1. Create `fix/` branch
2. Write test that reproduces bug
3. Fix the bug
4. Ensure test passes
5. Submit PR

---

## 💡 Feature Requests

### Suggesting Features

1. Check if feature already requested
2. Create issue describing:
   - Problem it solves
   - Proposed solution
   - Use cases
   - Alternatives considered

### Implementing Features

1. Discuss in issue first
2. Get approval from maintainers
3. Create `feature/` branch
4. Implement with tests
5. Submit PR

---

## 🏷️ Versioning

We follow [Semantic Versioning](https://semver.org/):
- **MAJOR**: Breaking changes
- **MINOR**: New features (backward compatible)
- **PATCH**: Bug fixes

---

## 📋 Pull Request Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing performed

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Comments added for complex code
- [ ] Documentation updated
- [ ] No new warnings
- [ ] Tests pass locally
```

---

## 🤝 Code of Conduct

- Be respectful and inclusive
- Welcome newcomers
- Focus on constructive feedback
- Respect different opinions

---

## 📞 Getting Help

- **Documentation**: Check [README.md](README.md) and [docs/](docs/)
- **Issues**: Search existing issues or create new one
- **Discussions**: Use GitHub Discussions for questions

---

## 🙏 Thank You!

Your contributions make this project better. Thank you for taking the time to contribute!

---

**Last Updated**: 2026-01-04
