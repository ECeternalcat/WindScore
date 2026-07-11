version: 1.0.0-ui
name: Figma-app-ui-system
description: "A high-density product interface system adapting the bold editorial black-and-white frame for complex software environments. The UI chrome is strictly neutral — crisp panel boundaries, structured metadata, variable sans typography — while functional pastel blocks are reserved for interactive canvasses, contextual notifications, focus states, and categorization. Built for deep focus and utility, maintaining the signature brand joy at a microscopic scale."

colors:

Core App Chrome (Monochrome Framework)

primary: "#000000"
on-primary: "#ffffff"
canvas-app: "#f5f5f5"         # App background behind workspace/canvas
canvas-panel: "#ffffff"       # Toolbars, sidebars, inspector panels
canvas-workspace: "#fbfbfa"   # Primary document/canvas workspace background

Text & Typography Roles (Essential for UI hierarchy contrast)

ink-primary: "#000000"        # Active commands, main labels, user input
ink-secondary: "#5c5c5c"      # Secondary labels, metadata, inactive tab text
ink-tertiary: "#999999"       # Placeholder text, disabled actions, keyboard shortcuts

UI Borders & Dividers

border-default: "#e6e6e6"     # Panel boundaries, input fields, active divisions
border-muted: "#f1f1f1"       # Subtle separators inside nested menus or list items
border-focus: "#000000"       # Active input focus ring

Interactive State Overlays

state-hover: "rgba(0, 0, 0, 0.04)"
state-active: "rgba(0, 0, 0, 0.08)"
state-selected: "rgba(0, 0, 0, 0.06)"
state-disabled: "rgba(0, 0, 0, 0.02)"

Functional Pastels (Preserved as UI containers, workspace stickies, and guide banners)

block-lime: "#dceeb1"         # Use: FAQ / Helpdesk panel, system onboarding banners
block-lilac: "#c5b0f4"        # Use: Premium features, Pro workspace indicators
block-cream: "#f4ecd6"        # Use: Canvas sticky notes, user comment pins
block-pink: "#efd4d4"         # Use: Warning prompts, action confirmation banners
block-mint: "#c8e6cd"         # Use: Feature updates, release announcements, success notifications
block-coral: "#f3c9b6"        # Use: Onboarding highlights, interactive tips
block-navy: "#1f1d3d"         # Use: Dark-mode modal overlays, command palettes (Cmd+K)

Semantics & Diagnostics

accent-magenta: "#ff3d8b"     # Notification dots, real-time cursor presence, live badges
semantic-success: "#1ea64a"
semantic-error: "#f23c3c"
overlay-scrim: "rgba(0, 0, 0, 0.40)"

typography:

Condensed scale optimized for data-dense toolbars and inspectors

ui-title-lg:
fontFamily: figmaSans
fontSize: 24px
fontWeight: 540
lineHeight: 1.20
letterSpacing: -0.48px
fontFeature: kern
ui-title-md:
fontFamily: figmaSans
fontSize: 18px
fontWeight: 540
lineHeight: 1.30
letterSpacing: -0.18px
fontFeature: kern
panel-header:
fontFamily: figmaSans
fontSize: 13px
fontWeight: 540
lineHeight: 1.40
letterSpacing: -0.10px
fontFeature: kern
body-default:
fontFamily: figmaSans
fontSize: 13px
fontWeight: 340
lineHeight: 1.45
letterSpacing: 0
fontFeature: kern
body-sm:
fontFamily: figmaSans
fontSize: 11px
fontWeight: 330
lineHeight: 1.40
letterSpacing: 0
fontFeature: kern
control-label:
fontFamily: figmaSans
fontSize: 12px
fontWeight: 450
lineHeight: 1.30
letterSpacing: -0.10px
fontFeature: kern
mono-meta:
fontFamily: figmaMono
fontSize: 11px
fontWeight: 400
lineHeight: 1.00
letterSpacing: 0.44px
fontFeature: kern

rounded:
xs: 2px                       # Input focus rings, selection handles
sm: 4px                       # Default UI elements (toolbar buttons, checkboxes, context menus, input fields)
md: 8px                       # Panels, popovers, toast alerts, dropdown menus
lg: 12px                      # Modal dialogs, embedded canvas modules
pill: 50px                    # Restricted to global high-impact CTAs (e.g., 'Share', 'Upgrade')
full: 9999px                  # User avatars, circular status switches

spacing:
hair: 1px                     # Divider borders
xxs: 2px                      # Micro layouts, nested metadata elements
xs: 4px                       # Control-to-label spacing, item list gap
sm: 8px                       # Default padding for buttons, list items, input fields
md: 12px                      # Panel interior padding, group margins
lg: 16px                      # Large dialog margins, workspace boundaries
xl: 24px                      # Space between major UI modules
xxl: 32px                     # Onboarding panels, empty state layouts

components:

Global Brand Call-To-Action (Retains Web's signature Pill design)

btn-global-primary:
backgroundColor: "{colors.primary}"
textColor: "{colors.on-primary}"
typography: "{typography.control-label}"
rounded: "{rounded.pill}"
padding: "8px 16px"
states:
hover: { scale: 0.98 }
active: { scale: 0.96 }

Standard Panel Actions (Space-efficient, rectangular)

btn-panel-secondary:
backgroundColor: "{colors.canvas-panel}"
border: "1px solid {colors.border-default}"
textColor: "{colors.ink-primary}"
typography: "{typography.control-label}"
rounded: "{rounded.sm}"
padding: "6px 12px"
states:
hover: { backgroundColor: "{colors.state-hover}" }
active: { backgroundColor: "{colors.state-active}" }
disabled: { opacity: 0.5, pointerEvents: "none" }

Icon-only Tool / Toolbar Buttons

btn-icon-tool:
backgroundColor: "transparent"
textColor: "{colors.ink-primary}"
rounded: "{rounded.sm}"
size: "28px"
states:
hover: { backgroundColor: "{colors.state-hover}" }
active: { backgroundColor: "{colors.state-active}" }
selected: { backgroundColor: "{colors.primary}", textColor: "{colors.on-primary}" }

Text & Numeric Inputs

ui-input-text:
backgroundColor: "{colors.canvas-panel}"
border: "1px solid {colors.border-default}"
textColor: "{colors.ink-primary}"
typography: "{typography.body-default}"
rounded: "{rounded.sm}"
padding: "6px 10px"
states:
focus: { borderColor: "{colors.border-focus}", outline: "1px solid {colors.border-focus}" }
disabled: { backgroundColor: "{colors.state-disabled}", textColor: "{colors.ink-tertiary}" }

Inspector / Control Dropdown Selectors

ui-select-trigger:
backgroundColor: "{colors.canvas-panel}"
border: "1px solid {colors.border-default}"
textColor: "{colors.ink-primary}"
typography: "{typography.body-default}"
rounded: "{rounded.sm}"
padding: "6px 8px 6px 10px"
states:
hover: { backgroundColor: "{colors.state-hover}" }

Sidebar & Navigation Links

panel-nav-item:
textColor: "{colors.ink-secondary}"
typography: "{typography.control-label}"
rounded: "{rounded.sm}"
padding: "6px 10px"
states:
hover: { backgroundColor: "{colors.state-hover}", textColor: "{colors.ink-primary}" }
selected: { backgroundColor: "{colors.state-selected}", textColor: "{colors.ink-primary}", fontWeight: 540 }

Popovers & Dropdown Overlays

ui-dropdown-menu:
backgroundColor: "{colors.canvas-panel}"
border: "1px solid {colors.border-default}"
rounded: "{rounded.md}"
shadow: "0 4px 12px rgba(0,0,0,0.08), 0 1px 3px rgba(0,0,0,0.02)"
padding: "4px"
minWidth: "160px"

Real-Time Presence Cursors (Combining magenta & name chips)

presence-cursor:
strokeColor: "{colors.accent-magenta}"
textColor: "{colors.on-primary}"
backgroundColor: "{colors.accent-magenta}"
typography: "{typography.body-sm}"
rounded: "{rounded.xs}"

Compact Onboarding / Guide Banner

panel-guide-toast:
backgroundColor: "{colors.block-lime}"
border: "1px solid rgba(0,0,0,0.06)"
textColor: "{colors.ink-primary}"
typography: "{typography.body-default}"
rounded: "{rounded.md}"
padding: "12px 16px"

Software UI Design Guidelines

Overview

Figma's marketing web aesthetics are driven by massive typography and high-impact color-block sections designed for quick narrative parsing. The Software UI system translates this identity into a compact, space-conscious, and interaction-first paradigm.

The application UI rests on a disciplined, highly dense monochrome layout. Sidebars, inspector panels, and toolbars are treated as structured white sheets ({colors.canvas-panel}) bounded by sharp 1px borders ({colors.border-default}).

The brand’s signature pastels do not serve as layout backgrounds here; instead, they function as interactive surfaces, semantic indicators, user-generated objects (sticky notes), and dismissible contextual alerts inside the app. This guarantees that user work remains focus-absolute, while preserving Figma's playful identity where it matters.

Typography Principles

Size-to-Weight Optimization: In dense UI environments, hierarchy is established by fine adjustments in weights rather than large shifts in size. The primary reading size is fixed at 13px ({typography.body-default}) using a light 340 weight, while active actions or table headers use the same size with a heavier weight of 540.

Monospace Metadata (figmaMono): Strictly reserved for attributes, keyboard shortcuts, version tags, and positional values (e.g., width, height, coordinates). It must always be set in uppercase with positive letter spacing ({typography.mono-meta}) to maintain its computational, technical flavor.

Tracking Compensation: Letter-spacing remains slightly negative on title sizes to preserve the "confident editorial" signature, but settles at 0 for sizes under 13px to maintain pixel-level legibility.

Layout & Sizing Architecture

The UI Grid: Built on a modular 4px grid system. Component heights are standardized across views:

Small actions / Inputs: 28px height.

Primary global buttons: 32px height.

Navigation rows: 28px height with a 6px 10px inner padding.

The Rounded System Separation:

{rounded.sm} ($4px$) is the default for controls (tool-buttons, select inputs, text fields, menu items). This allows crisp rectangular alignments without wasting horizontal screen space.

{rounded.pill} ($50px$) is strictly rationed for global actions, ensuring they stand out visually from the dense, modular grid of the application.

Depth & Elevations

Level

Elevation Spec

Application

Level 0

No shadow, 1px {colors.border-default}

Sidebar divisions, Inspector panels, Input borders

Level 1

0 2px 8px rgba(0,0,0,0.04)

Card overlays, Canvas templates

Level 2

0 4px 12px rgba(0,0,0,0.08), 0 1px 3px rgba(0,0,0,0.02)

Dropdowns, Contextual menus, Floating panels

Level 3

0 12px 32px rgba(0,0,0,0.12), 0 2px 8px rgba(0,0,0,0.04)

Centered Modal Dialogs, Critical setting wrappers

Do's and Don'ts

Do

Apply {colors.block-lime} or {colors.block-lilac} strictly as backgrounds for inline tips, user onboarding widgets, or status indicators.

Maintain consistent interactive states: ensure every clickable row in sidebar navigations registers hover changes with {colors.state-hover} and selected changes with {colors.state-selected}.

Rely on {colors.border-muted} ($#f1f1f1$) for secondary internal separations to avoid overly loud panels.

Don't

Do not use full-page pastel backgrounds outside user-created canvas environments; app chrome must always remain clean monochrome.

Do not use pill-rounded borders on text inputs or inspector selectors; they disrupt linear alignment and limit the space available for metadata.

Do not introduce mid-grays for disabled elements that conflict with the established {colors.ink-tertiary} and {colors.state-disabled} mappings.