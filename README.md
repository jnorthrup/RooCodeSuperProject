# IntelliJ Platform Plugin SDK Documentation

## Table of Contents
- [Development Environment](#development-environment)
- [Building Plugins](#building-plugins)
- [Plugin Structure](#plugin-structure)
- [Key Features and Capabilities](#key-features-and-capabilities)
- [Distribution and Testing](#distribution-and-testing)
- [Learning Resources](#learning-resources)
- [SDK Configuration](#sdk-configuration)
- [Practical Implementations](#practical-implementations)

## Development Environment

### IDE Requirements
- IntelliJ IDEA Community Edition or Ultimate Edition (latest version recommended)
- Essential bundled plugins:
  - Plugin DevKit
  - Gradle (bundled until version 2023.2)

## Building Plugins

### Gradle Approach (Recommended)
- Uses IntelliJ Platform Gradle Plugin
- Benefits:
  - Simplified dependency management
  - Streamlined packaging
  - Easy deployment to JetBrains Marketplace

### DevKit Approach
- Legacy workflow
- Still supported for:
  - Existing projects
  - Theme plugins

## Plugin Structure

### Essential Components
- **plugin.xml**
  - Plugin metadata
  - Extension points
  - Dependencies
- **Project Setup Options**
  - New Project Wizard with plugin generator
  - [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
    - Includes CI workflows
    - Pre-configured settings

## Key Features and Capabilities

### Extensibility Options
- UI enhancements
- Language support
- Build system integration
- Debugging tools

### PSI (Program Structure Interface)
- Core functionality:
  - File parsing
  - Syntactic model building
  - Semantic model construction
- Powers features:
  - Code completion
  - Inspections
  - Refactoring

### Documentation Support
- Custom language documentation
- IDE documentation extensions

## Distribution and Testing

### JetBrains Marketplace
- Official distribution platform
- Supports all IntelliJ-based IDEs

### Testing Infrastructure
- Automated testing capabilities
- Plugin functionality verification

## Learning Resources

### Official Documentation
- Comprehensive coverage:
  - Project setup
  - Platform architecture
  - Custom language development
- Note: Some advanced topics may require additional research

### Community Resources
- Tutorials
- Practical examples
- Community guides

## SDK Configuration

### SDK Management
- Configurable SDKs for specific frameworks
- JDK setup for Java development

## Practical Implementations

### IDE Integration
- **Bao-Cline**
  - VSCode extension that bridges VSCode with IntelliJ's PSI and AST capabilities
  - Features:
    - Direct file editing with diff views
    - Source code AST analysis
    - Project structure exploration
    - Terminal command execution
    - Browser-based testing
  - Enables VSCode users to leverage IntelliJ's powerful code analysis features

### PSI Tree Processing
- **PSIMiner**
  - Tool for processing PSI trees from IntelliJ Platform
  - Creates datasets for ML pipelines
  - Supports Java and Kotlin
  - Features:
    - Tree transformations
    - Filtering capabilities
    - Label extraction
    - Multiple storage formats

### AST Transformations
- **Bumblebee**
  - Plugin for Python AST transformations using PSI elements
  - Available transformations:
    - Code anonymization
    - Constant folding
    - Dead code removal
    - Expression unification
    - And more
  - Can be used as both:
    - Standalone tool
    - Library in other plugins

## References

1. [Developing a Plugin](https://plugins.jetbrains.com/docs/intellij/developing-plugins.html)
2. [About This Guide](https://plugins.jetbrains.com/docs/intellij/about.html)
3. [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/intellij-platform.html)
4. [Creating Your First Plugin](https://intellij-sdk-docs-cn.github.io/intellij/sdk/docs/basics/getting_started.html)
5. [Prerequisites](https://plugins.jetbrains.com/docs/intellij/prerequisites.html)
6. [Documentation](https://plugins.jetbrains.com/docs/intellij/documentation-provider.html)
7. [Community Tutorials](https://www.reddit.com/r/IntelliJIDEA/comments/mpr54h/creating_intellij_idea_plugins_beginner_and/)
8. [SDKs Documentation](https://www.jetbrains.com/help/idea/sdk.html)
9. [Documentation Guide](https://plugins.jetbrains.com/docs/intellij/documentation.html)
10. [API Explorer](https://plugins.jetbrains.com/docs/intellij/explore-api.html)