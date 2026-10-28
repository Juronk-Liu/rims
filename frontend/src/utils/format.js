import dayjs from 'dayjs';

export const money = (value) => {
  if (value === null || value === undefined || value === '') return '—';
  const num = Number(value);
  if (Number.isNaN(num)) return '—';
  return `¥${num.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`;
};

export const wan = (value) => {
  const num = Number(value || 0);
  if (num >= 10000) return `${(num / 10000).toFixed(num % 10000 === 0 ? 0 : 1)} 万`;
  return num.toLocaleString('zh-CN');
};

export const date = (value) => (value ? dayjs(value).format('YYYY-MM-DD') : '—');

export const datetime = (value) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—');

export const daysLeftText = (days) => {
  if (days === null || days === undefined) return '—';
  if (days < 0) return `逾期 ${Math.abs(days)} 天`;
  if (days === 0) return '今日到期';
  return `剩 ${days} 天`;
};

export const relativeTime = (value) => {
  if (!value) return '';
  const diff = dayjs().diff(dayjs(value), 'day');
  if (diff === 0) return '今天';
  if (diff === 1) return '昨天';
  if (diff < 30) return `${diff} 天前`;
  return dayjs(value).format('YYYY-MM-DD');
};
