# 🤝 Contributing to Otaku Reader

Thank you for your interest in contributing! This document will help you get started.

## 🚀 Quick Start

1. **Fork** the repository
2. **Clone** your fork: `git clone https://github.com/YOUR_USERNAME/Otaku-Reader.git`
3. **Create a branch**: `git checkout -b feature/your-feature-name`
4. **Make changes** following our guidelines
5. **Commit** with clear messages
6. **Push** and create a Pull Request

## 📋 Development Setup

### Requirements
- Android Studio Koala or newer
- JDK 21
- Android SDK (API 26-35)

### Build
```bash
./gradlew assembleDebug
```

### Run Tests
```bash
./gradlew test
./gradlew connectedAndroidTest
```

## 🏗️ Architecture Guidelines

We follow **Clean Architecture** with **MVI** pattern:

```
feature/
├── src/main/java/
│   ├── FeatureScreen.kt      # UI (Compose)
│   ├── FeatureViewModel.kt   # State management
│   ├── FeatureMvi.kt         # Contract (State, Event, Effect)
│   └── navigation/           # Navigation setup
```

### Code Style
- **Kotlin**: Official Kotlin style guide
- **Compose**: Material 3 components
- **Naming**: Clear, descriptive names
- **Comments**: Document complex logic

## 📝 Commit Message Format

```
type(scope): subject

body (optional)

footer (optional)
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `style`: Formatting
- `refactor`: Code restructuring
- `test`: Tests
- `chore`: Build/config

**Examples:**
```
feat(reader): Add dual-page spread mode

fix(library): Correct unread badge count

docs: Update README with new features
```

## 🎯 What We're Looking For

### High Priority
- 🐛 Bug fixes
- 🚀 Performance improvements
- 📖 Documentation
- 🧪 Tests

### Medium Priority
- ✨ New reader features
- 🎨 UI/UX improvements
- 🔌 Extension enhancements

### Future
- ☁️ Cloud sync features
- 🤖 AI recommendations

## 🐛 Reporting Bugs

Use the [Bug Report template](.github/ISSUE_TEMPLATE/bug_report.md) and include:
- Android version
- App version
- Steps to reproduce
- Expected vs actual behavior
- Screenshots if applicable

## 💡 Suggesting Features

Use the [Feature Request template](.github/ISSUE_TEMPLATE/feature_request.md) and describe:
- Problem you're solving
- Proposed solution
- Alternatives considered

## 🔒 Security

Report security vulnerabilities privately to the maintainers.

## ❓ Questions?

- Open a [Discussion](https://github.com/Heartless-Veteran/Otaku-Reader/discussions)
- Join our Discord (coming soon)

## 🙏 Recognition

Contributors will be recognized in our README and release notes!

---

By contributing, you agree that your contributions will be licensed under the Apache 2.0 License.
