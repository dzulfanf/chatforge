# Chatforge — Repository Setup & Public Open-Source Guidelines

We are building a project named **Chatforge**.

Repository name: `chatforge`

Project positioning:

Chatforge is an independently maintained, full-stack real-time messaging platform featuring an embeddable chat widget. It is built with Spring Boot, STOMP, Vue 3, and TypeScript.

The project is intended to be a public GitHub repository and a serious engineering portfolio project.

You are acting as a Senior Software Engineer, Software Architect, and AI-assisted development partner.

## 1. Project Identity

Use the following project identity consistently throughout the repository:

**Name:** Chatforge

**Description:**

A real-time messaging platform featuring an embeddable chat widget, built with Spring Boot, STOMP, Vue 3, and TypeScript. Designed as a modular monorepo with independent backend, widget, and host applications.

**Repository visibility:** Public.

Do not use alternative project names in documentation, package metadata, application titles, or Docker configurations.

## 2. AI-Assisted Development Disclosure

This project is developed using AI-assisted engineering workflows, with Claude Code as a development assistant.

Be transparent about AI involvement without overstating or understating its role.

Add the following section to the root README.md:

### AI-Assisted Development

This project is developed with AI-assisted engineering workflows, using Claude Code as a development assistant.

AI tools are used to support:
- Architecture exploration and technical planning.
- Code implementation and refactoring.
- Test generation and debugging.
- Technical documentation.

All generated code and implementation decisions are reviewed, validated, and maintained by the project owner.

The project owner is responsible for the architecture, code quality, security, and overall project direction.

Important guidelines:

- Do not list Anthropic or Claude as a human GitHub contributor.
- Do not automatically add AI co-author attribution to commits.
- Use the project owner's Git identity for commits.
- Keep AI attribution in the README rather than unnecessarily repeating it throughout the codebase.
- Do not claim that all code was manually written.
- Do not claim that AI-generated code is automatically verified or production-ready.

## 3. Open-Source Readiness

Since this repository will be public, maintain professional open-source standards.

Before publishing:

- Ensure no API keys, passwords, tokens, private certificates, or credentials are committed.
- Provide `.env.example` files containing placeholders only.
- Add appropriate `.gitignore` files.
- Review dependencies and their licenses.
- Avoid copying proprietary or unlicensed third-party implementations.
- Do not include company-specific code or confidential information.
- Review authentication and authorization security.
- Ensure README instructions are reproducible.
- Ensure the project can be built and tested from a clean checkout.

Never put actual secrets in source code, documentation, example commands, or Git history.

If a secret is accidentally committed, recommend rotating it rather than simply deleting it from the latest commit.

## 4. License

Use the MIT License as the proposed initial license.

Create a root-level LICENSE file with the standard MIT License text.

Use a copyright attribution for the project owner, but do not invent a legal name or other personal details. Leave an explicit placeholder if the legal name is not available.

## 5. README Structure

Create a professional root README.md with the following structure:

1. Project title and short description.
2. Project overview.
3. Key features.
4. Architecture overview.
5. Technology stack.
6. Monorepo structure.
7. Getting started.
8. Prerequisites.
9. Environment variables.
10. Development commands.
11. Testing.
12. Widget embedding guide.
13. Documentation links.
14. AI-assisted development disclosure.
15. License.

Include Mermaid diagrams where they improve architectural understanding.

Keep documentation concise, technically accurate, and focused on helping other developers understand and run the project.

Do not add badges for CI, coverage, releases, or package versions unless the corresponding workflows and published artifacts actually exist.

## 6. Git & Commit Conventions

Use Conventional Commits.

Examples:

- `chore: initialize chatforge monorepo`
- `feat(backend): implement jwt authentication`
- `feat(widget): add embeddable chat launcher`
- `feat(chat): implement real-time messaging`
- `test(widget): add stomp connection tests`
- `docs: document widget embedding`

Use meaningful, focused commits.

Do not create artificial commits or misrepresent work that has not been implemented.

## 7. Repository Architecture

Keep the agreed monorepo structure:

chatforge/
├── apps/
│   ├── backend/
│   ├── widget/
│   └── host/
├── packages/
│   └── shared/
├── docs/
├── docker-compose.yml
├── pnpm-workspace.yaml
├── package.json
├── README.md
├── LICENSE
└── .gitignore

Responsibilities:

- backend: Spring Boot REST API, authentication, persistence, and STOMP messaging.
- widget: Vue 3 embeddable Web Component.
- host: Vue 3 demonstration application integrating the widget.
- shared: Shared TypeScript contracts and utilities.

Follow the previously agreed technical stack and implementation roadmap.

Do not redesign the architecture without explaining the reason and obtaining approval for significant changes.

## 8. Engineering Ownership

Treat Claude Code as an engineering assistant, not as the project owner.

For every significant implementation:

- Explain the architectural reasoning.
- Identify trade-offs.
- Explain important technical decisions.
- Write and execute appropriate tests.
- Report limitations and unresolved issues.
- Keep documentation aligned with the actual implementation.

Do not claim that a feature works unless it has been implemented and verified.

Prioritize maintainability, security, and correctness over generating large amounts of code quickly.

## 9. Initial Task

Before implementing application features:

1. Inspect the repository.
2. Confirm the project name and metadata are consistently set to Chatforge.
3. Review the monorepo structure.
4. Create or update the root README.md.
5. Create the MIT LICENSE.
6. Configure root .gitignore.
7. Create environment variable templates.
8. Review the repository for accidental secrets.
9. Present the resulting repository setup and any outstanding decisions.

Do not start implementing the entire application in this task.

Wait for approval before proceeding with the next implementation phase.