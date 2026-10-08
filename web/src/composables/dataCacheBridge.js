/**
 * 数据缓存清理桥
 *
 * useSession 在登出时需要清理 useAcademicData 持有的本地缓存，
 * 但 useAcademicData 依赖 useSession（需要读取会话状态），直接互相 import 会形成循环依赖。
 * 这里用一个极小的注册表把两者解耦：useAcademicData 注册清理函数，useSession 触发清理。
 */

const cleaners = [];

/**
 * 注册一个本地数据缓存清理函数
 * @param {Function} fn 无参清理函数
 */
export function registerDataCacheCleaner(fn) {
  if (typeof fn === 'function' && !cleaners.includes(fn)) {
    cleaners.push(fn);
  }
}

/** 执行全部已注册的缓存清理函数（登出 / 切换账号时调用） */
export function clearRegisteredDataCaches() {
  cleaners.forEach(fn => {
    try {
      fn();
    } catch {
      // 单个清理器异常不影响其它清理器
    }
  });
}
