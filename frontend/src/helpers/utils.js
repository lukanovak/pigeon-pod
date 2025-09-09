import { notifications } from '@mantine/notifications';
import { toastConstants } from '../constants/toast.constants.js';
import '@mantine/notifications/styles.css';
import i18next from 'i18next';

export function showError(error) {
  if (error.message) {
    if (error.name === 'AxiosError') {
      switch (error.response.status) {
        case 401: {
          const searchParams = new URLSearchParams(window.location.search);
          const hasExpired = searchParams.get('expired') === 'true';
          if (!hasExpired) {
            window.location.href = '/login?expired=true';
            localStorage.removeItem('user');
          }
          break;
        }
        case 429:
          notifications.show({
            color: toastConstants.ERROR_COLOR,
            title: i18next.t('error'),
            message: 'Too many requests, please try again later.',
            position: 'top-right',
            autoClose: toastConstants.ERROR_TIMEOUT,
          });
          break;
        case 500:
          notifications.show({
            color: toastConstants.ERROR_COLOR,
            title: i18next.t('error'),
            message: 'Internal server error, please contact administrator.',
            position: 'top-right',
            autoClose: toastConstants.ERROR_TIMEOUT,
          });
          break;
        default:
          notifications.show({
            color: toastConstants.ERROR_COLOR,
            title: i18next.t('error'),
            message: error.message,
            position: 'top-right',
            autoClose: toastConstants.ERROR_TIMEOUT,
          });
      }
      return;
    }
    notifications.show({
      color: toastConstants.ERROR_COLOR,
      title: i18next.t('error'),
      message: error.message,
      position: 'top-right',
      autoClose: toastConstants.ERROR_TIMEOUT,
    });
  } else {
    notifications.show({
      color: toastConstants.ERROR_COLOR,
      title: i18next.t('error'),
      message: error,
      position: 'top-right',
      autoClose: toastConstants.ERROR_TIMEOUT,
    });
  }
}

export function showWarning(message) {
  notifications.show({
    color: toastConstants.WARNING_COLOR,
    title: i18next.t('warning'),
    message: message,
    position: 'top-right',
    autoClose: toastConstants.WARNING_TIMEOUT,
  });
}

export function showSuccess(message) {
  notifications.show({
    color: toastConstants.SUCCESS_COLOR,
    title: i18next.t('success'),
    message: message,
    position: 'top-right',
    autoClose: toastConstants.SUCCESS_TIMEOUT,
  });
}

export function showInfo(message) {
  notifications.show({
    color: toastConstants.INFO_COLOR,
    title: i18next.t('info'),
    message: message,
    position: 'top-right',
    autoClose: toastConstants.INFO_TIMEOUT,
  });
}

export function showNotice(message) {
  notifications.show({
    color: toastConstants.NOTICE_COLOR,
    title: i18next.t('notice'),
    message: message,
    position: 'top-right',
    autoClose: toastConstants.NOTICE_TIMEOUT,
  });
}

/**
 * 解析 ISO 8601 Duration (例如 PT1H11M52S)
 * 秒数四舍五入到分钟，不直接显示秒
 */
export function formatISODuration(isoDuration) {
  if (!isoDuration) {
    return '';
  }
  const regex = /P(?:([0-9]+)D)?T?(?:([0-9]+)H)?(?:([0-9]+)M)?(?:([0-9]+)S)?/;
  const matches = isoDuration.match(regex);

  if (!matches) return isoDuration;

  const days = parseInt(matches[1] || 0, 10);
  const hours = parseInt(matches[2] || 0, 10);
  let minutes = parseInt(matches[3] || 0, 10);
  const seconds = parseInt(matches[4] || 0, 10);

  // 四舍五入秒 → 分钟
  if (seconds >= 30) {
    minutes += 1;
  }

  // 转换天数为小时
  let totalHours = days * 24 + hours;

  let parts = [];
  if (totalHours > 0) {
    // 小时模式
    if (minutes >= 60) {
      totalHours += Math.floor(minutes / 60);
      minutes = minutes % 60;
    }
    parts.push(`${totalHours} 小时`);
    if (minutes > 0) parts.push(`${minutes} 分`);
  } else {
    // 分钟模式（没有小时）
    const totalMinutes = days * 24 * 60 + minutes;
    parts.push(`${totalMinutes} 分`);
  }

  return parts.join(' ');
}

/**
 * 格式化 ISO 日期时间
 * 2025-08-25T01:00:28 -> 2025-08-25 01:00:28
 */
export function formatISODateTime(isoDateTime) {
  const date = new Date(isoDateTime);
  const pad = (n) => n.toString().padStart(2, '0');

  const year = date.getFullYear();
  const month = pad(date.getMonth() + 1);
  const day = pad(date.getDate());
  // const hour = pad(date.getHours());
  // const minute = pad(date.getMinutes());
  // const second = pad(date.getSeconds());

  // return `${year}-${month}-${day} ${hour}:${minute}:${second}`;
  return `${year}-${month}-${day}`;
}

export function openPage(url) {
  window.open(url);
}

export function removeTrailingSlash(url) {
  if (url.endsWith('/')) {
    return url.slice(0, -1);
  } else {
    return url;
  }
}
