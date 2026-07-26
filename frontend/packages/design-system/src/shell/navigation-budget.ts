import type { NavigationItem } from "./ProductShell";

const MAX_TOP_LEVEL_NAVIGATION = 6;

/**
 * 校验顶级导航预算，防止新增功能时把产品重新堆成菜单集合。
 */
export function assertNavigationBudget(navigation: readonly NavigationItem[]) {
  if (navigation.length > MAX_TOP_LEVEL_NAVIGATION) {
    throw new Error(`顶级导航不能超过 ${MAX_TOP_LEVEL_NAVIGATION} 项`);
  }
}
