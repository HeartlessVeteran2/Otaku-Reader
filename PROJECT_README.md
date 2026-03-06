# 🚀 Otaku Reader Development

## Personal Manga Reader with AI-Powered Features

A modern Android manga reader built with Kotlin, Jetpack Compose, and Clean Architecture.

---

## 🎯 Project Status

| Phase | Status |
|-------|--------|
| Repository Setup | ✅ Complete |
| Build Configuration | ✅ Complete |
| Branding | ⚠️ In Progress |
| Core Features | 📋 Planned |
| AI Integration | 📋 Planned |
| Extension System | 📋 Planned |

---

## 📊 Sprint Board

### 🔴 P0 - Critical
- [ ] Push branding changes (Komikku → Otaku Reader)
- [ ] Fix package names (app.komikku → app.otakureader)

### 🟡 P1 - High  
- [ ] Implement Library feature
- [ ] Implement Reader feature
- [ ] Set up Room database

### 🟢 P2 - Medium
- [ ] AI Features integration
- [ ] Extension system

### ⚪ P3 - Low
- [ ] UI polish
- [ ] Documentation

---

## 🏗️ Architecture

```
Otaku-Reader/
├── app/              # Application shell
├── core/             # Platform modules
│   ├── common/       # Shared utilities
│   ├── database/     # Room database
│   ├── network/      # HTTP client
│   ├── preferences/  # DataStore
│   ├── ui/           # Compose components
│   └── navigation/   # Navigation
├── domain/           # Business logic
│   ├── model/        # Domain models
│   ├── repository/   # Interfaces
│   └── usecase/      # Use cases
├── data/             # Data layer
│   ├── local/        # Database impl
│   ├── remote/       # API clients
│   └── repository/   # Impl
├── feature/          # UI features
│   ├── library/
│   ├── reader/
│   ├── browse/
│   ├── updates/
│   ├── history/
│   └── settings/
└── source-api/       # Extension API
```

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose |
| DI | Hilt |
| Database | Room |
| Network | OkHttp + Retrofit |
| Images | Coil |
| Async | Coroutines + Flow |
| AI | Gemini API |
| Local AI | Ollama |

---

## 🚧 Current Blockers

- Azure DNS blocks `dl.google.com` (Google Maven)
- Workaround: Build locally or use GitHub Actions

---

## 📅 Roadmap

### Phase 1: Foundation (Week 1-2)
- [x] Repository structure
- [x] Build configuration
- [ ] Branding complete
- [ ] Working build

### Phase 2: Core Features (Week 3-6)
- [ ] Library management
- [ ] Reader engine
- [ ] Browse sources
- [ ] Downloads

### Phase 3: AI Features (Week 7-10)
- [ ] Smart recommendations
- [ ] SFX translation
- [ ] Content understanding

### Phase 4: Extensions (Week 11-14)
- [ ] Source API
- [ ] Extension loader
- [ ] Repository system

---

## 👥 Team

- [@HeartlessVeteran2](https://github.com/HeartlessVeteran2) — Lead Developer

---

## 📚 Resources

- [Issue #8 - AI Features](../../issues/8)
- [Issue #10 - Extension System](../../issues/10)
- [Issue #12 - Master Tracking](../../issues/12)

---

*Last updated: 2026-03-07 by Kimi Claw*
