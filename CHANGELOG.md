# Changelog — Unified SAMAR + Local AI

## 2026-11-24 — Unified package
- Combined the complete SAMAR project and Local AI integration into one active Android project tree.
- Added LiteRT-LM Android runtime dependency and LocalModelRegistry provider.
- Added LOCAL AI model import control to the SAMAR HUD.
- Updated chat routing to cloud backend -> local generative AI -> existing rule-based offline fallback.
- Updated HUD status to distinguish ONLINE, LOCAL AI, and BASIC OFFLINE modes.
- Preserved the four original project/milestone ZIPs under `project-history/`.
- KAVACH is intentionally postponed and excluded from this milestone.
- Model weights are not bundled; no APK build or actual-device inference is claimed as verified.
