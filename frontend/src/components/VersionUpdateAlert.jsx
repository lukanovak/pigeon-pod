import React, { useEffect, useState } from 'react';
import { Alert, Button, Group, Text, Anchor, Box } from '@mantine/core';
import { useTranslation } from 'react-i18next';

const VersionUpdateAlert = () => {
  const { t } = useTranslation();
  const [showAlert, setShowAlert] = useState(false);
  const [updateInfo, setUpdateInfo] = useState(null);

  // 版本比较函数（支持 v 前缀与预发布后缀，如 1.7.0-beta）
  const compareVersions = (version1, version2) => {
    const normalize = (v) => (v || '').toString().trim().replace(/^v/i, '').split('-')[0];

    const toParts = (v) =>
      normalize(v)
        .split('.')
        .map((n) => {
          const num = parseInt(n, 10);
          return Number.isFinite(num) ? num : 0;
        });

    const v1parts = toParts(version1);
    const v2parts = toParts(version2);

    for (let i = 0; i < Math.max(v1parts.length, v2parts.length); i++) {
      const v1part = v1parts[i] || 0;
      const v2part = v2parts[i] || 0;
      if (v1part > v2part) return 1;
      if (v1part < v2part) return -1;
    }
    return 0;
  };

  // 获取当前版本
  const getCurrentVersion = () => {
    const metaTag = document.querySelector('meta[name="version"]');
    return metaTag ? metaTag.getAttribute('content') : '1.4.0';
  };

  // 获取 localStorage 中的更新通知信息
  const getUpdateNotice = () => {
    try {
      const stored = localStorage.getItem('update_notice');
      return stored ? JSON.parse(stored) : null;
    } catch (error) {
      console.error('Error parsing update_notice from localStorage:', error);
      return null;
    }
  };

  // 保存更新通知信息到 localStorage
  const setUpdateNotice = (data) => {
    try {
      localStorage.setItem('update_notice', JSON.stringify(data));
    } catch (error) {
      console.error('Error saving update_notice to localStorage:', error);
    }
  };

  // 获取上次检查更新的时间
  const getLastUpdateCheck = () => {
    try {
      const stored = localStorage.getItem('update_check');
      return stored ? new Date(stored) : null;
    } catch (error) {
      console.error('Error parsing update_check from localStorage:', error);
      return null;
    }
  };

  // 保存检查更新的时间
  const setLastUpdateCheck = () => {
    try {
      localStorage.setItem('update_check', new Date().toISOString());
    } catch (error) {
      console.error('Error saving update_check to localStorage:', error);
    }
  };

  // 检查是否需要进行更新检查（30分钟间隔）
  const shouldCheckForUpdates = () => {
    const lastCheck = getLastUpdateCheck();
    if (!lastCheck) return true;

    const now = new Date();
    const timeDiff = now.getTime() - lastCheck.getTime();
    const thirtyMinutes = 30 * 60 * 1000; // 30分钟的毫秒数

    return timeDiff >= thirtyMinutes;
  };

  // 从 GitHub API 获取最新版本信息
  const fetchLatestVersion = async () => {
    try {
      const response = await fetch(
        'https://api.github.com/repos/aizhimou/pigeon-pod/releases/latest',
        {
          headers: {
            Accept: 'application/vnd.github+json',
            'X-GitHub-Api-Version': '2022-11-28',
          },
        },
      );

      if (!response.ok) {
        console.error('Error fetching latest version:', response);
      }

      const data = await response.json();
      return {
        tag_name: data.tag_name,
        html_url: data.html_url,
      };
    } catch (error) {
      console.error('Error fetching latest version:', error);
      return null;
    }
  };

  // 检查版本更新
  const checkForUpdates = async () => {
    const currentVersion = getCurrentVersion();
    const existingNotice = getUpdateNotice();
    const now = new Date();

    // 1) 本地提示优先：不受 30 分钟节流限制
    if (existingNotice) {
      const isNewerThanCurrent = compareVersions(existingNotice.tag_name, currentVersion) > 0;
      if (!isNewerThanCurrent) {
        // 本地记录已过时（<= 当前版本），清理
        try {
          localStorage.removeItem('update_notice');
        } catch (_) {}
      } else if (!existingNotice.skip) {
        const noticeTime = new Date(existingNotice.notice_time);
        if (now >= noticeTime) {
          setUpdateInfo(existingNotice);
          setShowAlert(true);
        }
      }
    }

    // 2) 远端检查（受 30 分钟节流限制）
    if (!shouldCheckForUpdates()) {
      return;
    }

    const latestVersion = await fetchLatestVersion();
    if (!latestVersion) {
      return;
    }

    // 更新检查时间
    setLastUpdateCheck();

    // 仅当远端版本高于当前运行版本时才考虑提示
    if (compareVersions(latestVersion.tag_name, currentVersion) <= 0) {
      return;
    }

    // 覆盖策略：
    // - 没有本地记录
    // - 或远端版本新于本地记录
    // - 或本地记录被跳过（跳过仅对该版本有效）
    if (
      !existingNotice ||
      compareVersions(latestVersion.tag_name, existingNotice.tag_name) > 0 ||
      existingNotice.skip
    ) {
      const newUpdateInfo = {
        tag_name: latestVersion.tag_name,
        html_url: latestVersion.html_url,
        skip: false,
        notice_time: now.toISOString(),
      };
      setUpdateNotice(newUpdateInfo);
      setUpdateInfo(newUpdateInfo);
      setShowAlert(true);
    }
  };

  // 处理"跳过此版本"按钮点击
  const handleSkipVersion = () => {
    if (updateInfo) {
      const updatedInfo = {
        ...updateInfo,
        skip: true,
      };
      setUpdateNotice(updatedInfo);
      setShowAlert(false);
    }
  };

  // 处理"晚点再提示"按钮点击
  const handleRemindLater = () => {
    if (updateInfo) {
      const now = new Date();
      const eightHoursLater = new Date(now.getTime() + 8 * 60 * 60 * 1000); // 8小时后

      const updatedInfo = {
        ...updateInfo,
        notice_time: eightHoursLater.toISOString(),
      };
      setUpdateNotice(updatedInfo);
      setShowAlert(false);
    }
  };

  // 处理版本号点击
  const handleVersionClick = () => {
    if (updateInfo && updateInfo.html_url) {
      window.open(updateInfo.html_url, '_blank');
    }
  };

  useEffect(() => {
    checkForUpdates();
  }, []);

  if (!showAlert || !updateInfo) {
    return null;
  }

  // 创建自定义标题内容
  const customTitle = (
    <Group justify="space-between" align="center">
      <Box mr="md">
        <Text fw={500}>
          {t('new_version_available_description')}
          {': '}
          <Anchor
            component="button"
            onClick={handleVersionClick}
            fw={600}
            c="red.8"
            td="underline"
            style={{ cursor: 'pointer' }}
          >
            {updateInfo.tag_name}
          </Anchor>
        </Text>
      </Box>
      <Group gap="sm" wrap="nowrap">
        <Button size="xs" color="orange.8" variant="outline" onClick={handleRemindLater}>
          {t('remind_later')}
        </Button>
        <Button size="xs" color="orange.8" variant="outline" onClick={handleSkipVersion}>
          {t('skip_this_version')}
        </Button>
      </Group>
    </Group>
  );

  return (
    <Alert
      variant="light"
      radius="sm"
      title={customTitle}
      onClose={() => setShowAlert(false)}
      color="orange.8"
      mb="lg"
    ></Alert>
  );
};

export default VersionUpdateAlert;
