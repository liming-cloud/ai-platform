import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import {
  enterpriseBrand,
  enterpriseDarkTheme,
  enterpriseHighContrastTheme,
  enterpriseLightTheme,
  themeForPreferences,
} from '../theme';

const consoleStyles = readFileSync(new URL(['..', 'styles.css'].join('/'), import.meta.url), 'utf8');
const forcedColorRules = consoleStyles.slice(
  consoleStyles.indexOf('@media (forced-colors: active)'),
  consoleStyles.indexOf('@media (prefers-reduced-motion: reduce)'),
);

function relativeLuminance(color: string) {
  const channels = [1, 3, 5].map((offset) => Number.parseInt(color.slice(offset, offset + 2), 16) / 255);
  const [red, green, blue] = channels.map((channel) => channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4);
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}

function contrastRatio(foreground: string, background: string) {
  const values = [relativeLuminance(foreground), relativeLuminance(background)].sort((left, right) => right - left);
  return (values[0] + 0.05) / (values[1] + 0.05);
}

describe('enterprise theme', () => {
  it('maps the product accent and semantic surfaces through one Fluent theme', () => {
    expect(enterpriseLightTheme.colorBrandBackground).toBe(enterpriseBrand[80]);
    expect(enterpriseLightTheme.colorNeutralBackground2).toBe('#f5f7f9');
    expect(enterpriseLightTheme.fontFamilyBase).toContain('Segoe UI');
    expect(enterpriseLightTheme.borderRadiusMedium).toBe('6px');
  });

  it('offers dark and forced-colors themes without bypassing Fluent tokens', () => {
    expect(enterpriseDarkTheme.colorNeutralBackground1).not.toBe(enterpriseLightTheme.colorNeutralBackground1);
    expect(enterpriseDarkTheme.colorBrandForeground1).toBe(enterpriseBrand[110]);
    expect(enterpriseHighContrastTheme.colorNeutralBackground1).toBeDefined();
    expect(themeForPreferences({ dark: false, forcedColors: false })).toBe(enterpriseLightTheme);
    expect(themeForPreferences({ dark: true, forcedColors: false })).toBe(enterpriseDarkTheme);
    expect(themeForPreferences({ dark: true, forcedColors: true })).toBe(enterpriseHighContrastTheme);
  });

  it('separates brand metadata from surface metadata across theme and forced-color modes', () => {
    expect(consoleStyles).toContain('.on-brand-metadata');
    expect(consoleStyles).toContain('color: var(--colorNeutralForegroundOnBrand)');
    expect(consoleStyles).toContain('.surface-metadata');
    expect(consoleStyles).toContain('color: var(--colorNeutralForeground2)');
    expect(consoleStyles).toContain('@media (forced-colors: active)');
    expect(consoleStyles).toContain('color: CanvasText');
    expect(consoleStyles).not.toMatch(/\.eyebrow\s*\{/);
    expect(contrastRatio(enterpriseLightTheme.colorNeutralForeground2, enterpriseLightTheme.colorNeutralBackground1)).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(enterpriseDarkTheme.colorNeutralForeground2, enterpriseDarkTheme.colorNeutralBackground1)).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(enterpriseLightTheme.colorNeutralForegroundOnBrand, enterpriseLightTheme.colorBrandBackgroundStatic)).toBeGreaterThanOrEqual(4.5);
  });

  it('keeps navigation state and keyboard focus visible in system forced colors', () => {
    expect(forcedColorRules).toContain('.nav-item.is-active');
    expect(forcedColorRules).toContain('.nav-item:focus-visible');
    expect(forcedColorRules).toContain('outline: 2px solid Highlight');
    expect(forcedColorRules).toContain('.security-state::before');
  });

  it('allows long tenant and role context values to wrap at narrow and zoomed viewports', () => {
    expect(consoleStyles).toMatch(/@media \(max-width: 768px\) \{[\s\S]*\.topbar-context \{[^}]*white-space: normal;/);
    expect(consoleStyles).toMatch(/\.topbar-context > span \{[^}]*overflow-wrap: anywhere;/);
  });

  it('defines semantic layout variables from Fluent spacing and nudge tokens', () => {
    expect(consoleStyles).toMatch(/\.provider-root, \.fui-FluentProvider \{[\s\S]*--layout-space-compact/);
    expect(consoleStyles).toContain('--layout-space-compact: var(--spacingHorizontalSNudge, 6px)');
    expect(consoleStyles).toContain('--layout-space-related: var(--spacingHorizontalS, 8px)');
    expect(consoleStyles).toContain('--layout-space-default: var(--spacingHorizontalM, 12px)');
    expect(consoleStyles).toContain('--layout-space-section: var(--spacingHorizontalL, 16px)');
    expect(consoleStyles).toContain('--layout-space-panel: var(--spacingHorizontalXL, 20px)');
    expect(consoleStyles).toMatch(/\.master-detail \{[^}]*gap: var\(--layout-space-section\)/);
    expect(consoleStyles).toMatch(/\.detail-panel \{[^}]*padding: var\(--layout-space-panel\)/);
  });

  it('stacks master-detail workspaces at 1100px while preserving the detail region', () => {
    expect(consoleStyles).toMatch(/@media \(max-width: 1100px\) \{[\s\S]*\.master-detail \{[^}]*grid-template-columns: 1fr;/);
    expect(consoleStyles).toMatch(/@media \(max-width: 1100px\) \{[\s\S]*\.detail-panel \{[^}]*position: static;/);
  });

  it('provides 44px effective targets for coarse pointers and 390px form controls', () => {
    const touchRules = consoleStyles.slice(consoleStyles.indexOf('@media (max-width: 480px), (pointer: coarse)'));
    for (const selector of ['.fui-Input', '.fui-Select', '.fui-Combobox', '.fui-Checkbox', '.fui-Button']) {
      expect(touchRules).toContain(selector);
    }
    expect(touchRules).toContain('min-height: var(--control-touch-target-min)');
    expect(touchRules).toContain('min-width: var(--control-touch-target-min)');
  });

  it('keeps the login product title within the enterprise heading scale', () => {
    const loginHeadingRule = consoleStyles.match(/\.login-context h1 \{[^}]+\}/)?.[0] || '';
    expect(loginHeadingRule).toContain('48px');
    expect(loginHeadingRule).not.toContain('64px');
  });
});
