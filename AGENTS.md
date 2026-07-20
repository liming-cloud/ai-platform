# AGENTS.md

## engineering-assistant plugin protocol

When a user asks to use the `engineering-assistant` plugin, do not only claim it verbally and do not substitute generic skills by default.

Required protocol:

1. Select the active plugin runtime
   - Prefer the plugin capabilities exposed by the current agent runtime, such as plugin-provided skills or MCP tools already listed in the current tool context.
   - Do not assume Hermes is the only plugin runtime.
   - Use Hermes commands only when one of these is true:
     - the user explicitly asks to use Hermes;
     - the current execution environment is Hermes CLI;
     - no current-runtime plugin capability is available and a Hermes installation must be checked as a fallback.

2. Route to the minimum sufficient plugin skill
   - If the current runtime exposes plugin-qualified skills, choose the minimum sufficient `engineering-assistant:<skill-id>` skill from those capabilities.
   - If using Hermes, run:
     `hermes plugins list --plain --no-bundled | grep '^engineering-assistant\b' || true`
     then:
     `python3 ~/.hermes/plugins/engineering-assistant/engineering-assistant/scripts/route_skill.py --root ~/.hermes/plugins/engineering-assistant --prompt '<user request>'`
   - If a router is used, respect router output:
     - `selected`: use that plugin skill
     - `waiting_for_input`: do not guess; surface the minimal ambiguity
     - `rejected`: explain-only request; do not fake stage execution

3. Explicitly load the plugin-qualified skill
   - Load or invoke the plugin-qualified skill through the active runtime.
   - Do not treat a plugin availability check as proof the plugin was used.
   - Do not run Hermes-only commands merely to satisfy evidence reporting when the active runtime is not Hermes.

4. Consume control-plane assets when relevant
   - Read the smallest sufficient set from the active runtime's plugin assets.
   - For Hermes fallback, the asset paths are:
     - `~/.hermes/plugins/engineering-assistant/engineering-assistant/registry/workflows.yaml`
     - `~/.hermes/plugins/engineering-assistant/engineering-assistant/workflows/*.yaml`
     - `~/.hermes/plugins/engineering-assistant/engineering-assistant/runtime/compiled/skill-runtime-index.json`
     - relevant `standards/*` or `checklists/*`
   - Use these files to decide workflow, risk, explicit-only skills, gates, and expected artifacts

5. Validate plugin-style stage outputs
   - If a StageRunResult JSON or equivalent stage artifact is produced, validate it with the validator for the active runtime.
   - For Hermes fallback, run:
     `python3 ~/.hermes/plugins/engineering-assistant/engineering-assistant/scripts/validate_stage_run_result.py <result.json>`
   - If validation fails, do not claim success

6. Response evidence requirement
   - Final reports must state:
     - active plugin runtime
     - plugin availability or capability source
     - routed skill id
     - explicitly loaded plugin-qualified skill
     - workflow/control-plane files consumed
     - validator result if applicable

## codegraph usage protocol

The current repository already has a working codegraph index. For code understanding, architecture tracing, impact analysis, and non-trivial debugging, prefer codegraph as the first discovery tool instead of blind file search.

Required protocol:

1. Use `mcp_codegraph_codegraph_explore` first for almost all codebase questions
   - Apply this to questions like:
     - how does feature X work
     - where is module Y implemented
     - why does flow Z fail
     - what should change for a refactor
     - what code paths touch entity/service/controller/repository A
   - Prefer a natural-language query or a compact bag of symbols/files
   - Do not start with broad `search_files`/grep when the goal is understanding behavior or architecture

2. Use the codegraph tool priority in this order
   - `mcp_codegraph_codegraph_explore`: primary entrypoint for understanding an area
   - `mcp_codegraph_codegraph_node`: when one specific symbol body or overload needs full detail
   - `mcp_codegraph_codegraph_impact`: before non-trivial refactors or schema/API changes
   - `mcp_codegraph_codegraph_callers` / `mcp_codegraph_codegraph_callees`: when tracing direct call relationships
   - `mcp_codegraph_codegraph_search`: only to find symbol names when the exact symbol is unknown
   - `mcp_codegraph_codegraph_files`: for indexed tree/layout inspection

3. Only fall back to `read_file` / `search_files` after codegraph narrows the target
   - Use file tools to inspect raw config, YAML, SQL, generated artifacts, or long non-indexed text
   - Use file tools when codegraph output is insufficient or when verifying exact surrounding text in a file
   - Do not reopen large files just to rediscover code already returned by `codegraph_explore`

4. Use codegraph before editing or reviewing risky code
   - Before changing important services, aggregates, controllers, repositories, routes, or shared contracts:
     - inspect the area with `codegraph_explore`
     - inspect the target symbol with `codegraph_node` if needed
     - run `codegraph_impact` for non-local changes
   - Summaries and implementation plans should mention the key symbols/files identified via codegraph when relevant

5. Mention codegraph evidence in the response when it materially informed the work
   - Examples:
     - explored `AuthApplicationService login SessionController`
     - impact checked for `KnowledgeBaseService`
     - traced callers of `ModelProviderService.save`

Practical rules:
- For architecture/debugging/refactor questions, codegraph is the default first step
- For one exact known file with a small targeted read, `read_file` is fine directly
- For repo-wide regex hunting in non-code assets, `search_files` is fine directly
- When codegraph and raw files disagree, verify with `read_file` and report the discrepancy explicitly

## High-risk plugin skills

Treat the following as explicit-only unless the user intent is explicit and the plugin router selects them:
- `engineering-assistant:workflow-orchestrator`
- `engineering-assistant:code-development`
- `engineering-assistant:implementation-controller`
- `engineering-assistant:code-quality-governor`
- `engineering-assistant:release-readiness`
- `engineering-assistant:release-verification`
- `engineering-assistant:engineering-knowledge-miner`

## Invalid behavior

The following does NOT count as using the plugin:
- only checking that `engineering-assistant` is installed
- only saying "using engineering-assistant" in prose
- using only generic skills when a relevant plugin-qualified skill exists

The following is invalid code investigation behavior for this repository:
- starting broad architecture/debugging/refactor analysis with `search_files` alone when codegraph could answer faster and better
- reading many whole files manually before trying `mcp_codegraph_codegraph_explore`
- claiming impact is understood for a non-trivial change without using `codegraph_impact` or equivalent codegraph tracing
