import React, { useEffect, useState } from 'react';
import { Alert, Button, Group, Text, Anchor, Box } from '@mantine/core';
import { useTranslation } from 'react-i18next';

const VersionUpdateAlert = () => {
  const { t } = useTranslation();
  const [showAlert, setShowAlert] = useState(false);
  const [updateInfo, setUpdateInfo] = useState(null);

  // 版本比较函数
  const compareVersions = (version1, version2) => {
    const v1parts = version1.replace(/^v/, '').split('.').map(Number);
    const v2parts = version2.replace(/^v/, '').split('.').map(Number);
    
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
      const response = await fetch('https://api.github.com/repos/aizhimou/pigeon-pod/releases/latest', {
        headers: {
          'Accept': 'application/vnd.github+json',
          'X-GitHub-Api-Version': '2022-11-28',
        },
      });

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
    // 检查是否需要进行更新检查
    if (!shouldCheckForUpdates()) {
      return;
    }

    // 获取最新版本信息
    const latestVersion = await fetchLatestVersion();
    if (!latestVersion) {
      return;
    }

    // 更新检查时间
    setLastUpdateCheck();

    const currentVersion = getCurrentVersion();
    const updateNotice = getUpdateNotice();

    // 如果有存储的更新通知信息
    if (updateNotice) {
      // 如果用户选择跳过此版本，则不显示提醒
      if (updateNotice.skip) {
        return;
      }

      // 检查是否到了提醒时间
      const noticeTime = new Date(updateNotice.notice_time);
      const now = new Date();
      
      if (now >= noticeTime) {
        setUpdateInfo(updateNotice);
        setShowAlert(true);
      }
    } else {
      // 没有存储的更新通知信息，检查是否有新版本
      if (compareVersions(latestVersion.tag_name, currentVersion) > 0) {
        const newUpdateInfo = {
          tag_name: latestVersion.tag_name,
          html_url: latestVersion.html_url,
          skip: false,
          notice_time: new Date().toISOString(),
        };

        setUpdateNotice(newUpdateInfo);
        setUpdateInfo(newUpdateInfo);
        setShowAlert(true);
      }
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
    <Group justify="space-between" align="center" >
      <Box mr="md">
        <Text fw={500}>
          {t('new_version_available_description')}{': '}
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
          <Button
            size="xs"
            color="orange.8"
            variant="outline"
            onClick={handleRemindLater}
          >
            {t('remind_later')}
          </Button>
          <Button
            size="xs"
            color="orange.8"
            variant="outline"
            onClick={handleSkipVersion}
          >
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
      color='orange.8'
      mb="lg"
    >
    </Alert>
  );
};

export default VersionUpdateAlert;
