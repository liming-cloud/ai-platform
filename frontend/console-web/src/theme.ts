import {
  createDarkTheme,
  createHighContrastTheme,
  createLightTheme,
  type BrandVariants,
  type Theme,
} from '@fluentui/react-components';

export const enterpriseBrand: BrandVariants = {
  10: '#061724',
  20: '#082338',
  30: '#0a304a',
  40: '#0b3d5e',
  50: '#0c4a72',
  60: '#0d5887',
  70: '#0e659c',
  80: '#0f6cbd',
  90: '#2a7dc4',
  100: '#448dcc',
  110: '#5f9dd4',
  120: '#7aaddc',
  130: '#96bee4',
  140: '#b3cfec',
  150: '#d0e0f3',
  160: '#edf2f8',
};

const fluentLightTheme = createLightTheme(enterpriseBrand);
const sharedEnterpriseTokens = {
  fontFamilyBase: '"Segoe UI Variable", "Segoe UI", system-ui, sans-serif',
  borderRadiusMedium: '6px',
  borderRadiusLarge: '8px',
} as const;

export const enterpriseLightTheme: Theme = {
  ...fluentLightTheme,
  colorNeutralBackground1: '#ffffff',
  colorNeutralBackground2: '#f5f7f9',
  colorNeutralBackground3: '#eef2f5',
  colorNeutralForeground1: '#17202a',
  colorNeutralForeground2: '#4d5967',
  colorNeutralStroke1: '#d9e0e6',
  colorNeutralStroke2: '#e6ebef',
  ...sharedEnterpriseTokens,
};

export const enterpriseDarkTheme: Theme = {
  ...createDarkTheme(enterpriseBrand),
  // Keep the small sidebar brand label above WCAG AA on the dark surface.
  colorBrandForeground1: enterpriseBrand[110],
  ...sharedEnterpriseTokens,
};

export const enterpriseHighContrastTheme: Theme = {
  ...createHighContrastTheme(),
  ...sharedEnterpriseTokens,
};

export function themeForPreferences({ dark, forcedColors }: { dark: boolean; forcedColors: boolean }): Theme {
  if (forcedColors) return enterpriseHighContrastTheme;
  return dark ? enterpriseDarkTheme : enterpriseLightTheme;
}
