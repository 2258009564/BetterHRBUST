/**
 * 哈尔滨理工大学官方标准上课时间（作息时间表）
 *
 * 小节（BASE，1~12 节）与 大节（COMBINE，1~6 节）的起止时间。
 * 数据来源：学校官方作息表
 *   第 1、2 节   8:10-8:55   9:05-9:50
 *   第 3、4 节  10:10-10:55 11:05-11:50
 *   第 5、6 节  13:30-14:15 14:25-15:10
 *   第 7、8 节  15:30-16:15 16:25-17:10
 *   第 9、10 节 18:00-18:45 18:55-19:40
 *   第 11、12 节 19:50-20:35 20:45-21:30
 */

export const BASE_SLOT_TIMES = [
  { period: 1, time: '08:10-08:55' },
  { period: 2, time: '09:05-09:50' },
  { period: 3, time: '10:10-10:55' },
  { period: 4, time: '11:05-11:50' },
  { period: 5, time: '13:30-14:15' },
  { period: 6, time: '14:25-15:10' },
  { period: 7, time: '15:30-16:15' },
  { period: 8, time: '16:25-17:10' },
  { period: 9, time: '18:00-18:45' },
  { period: 10, time: '18:55-19:40' },
  { period: 11, time: '19:50-20:35' },
  { period: 12, time: '20:45-21:30' }
];

export const COMBINE_SLOT_TIMES = [
  { period: 1, time: '08:10 - 09:50' },
  { period: 2, time: '10:10 - 11:50' },
  { period: 3, time: '13:30 - 15:10' },
  { period: 4, time: '15:30 - 17:10' },
  { period: 5, time: '18:00 - 19:40' },
  { period: 6, time: '19:50 - 21:30' }
];

/**
 * 取第 n 大节（COMBINE，1~6）的起止时间，如 '08:10 - 09:50'；越界返回空串。
 */
export function getCombineSlotTime(period) {
  return COMBINE_SLOT_TIMES.find(s => s.period === period)?.time || '';
}
