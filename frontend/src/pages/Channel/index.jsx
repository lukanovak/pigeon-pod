import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useDisclosure, useMediaQuery } from '@mantine/hooks';
import {
  ActionIcon,
  Container,
  Grid,
  Title,
  Text,
  Image,
  Button,
  Group,
  Card,
  Center,
  Stack,
  Flex,
  Badge,
  Box,
  Paper,
  Avatar,
  Modal,
  Loader,
  TextInput,
  NumberInput,
  Tooltip,
} from '@mantine/core';
import {
  IconBrandApplePodcast,
  IconClock,
  IconBrandYoutubeFilled,
  IconPlayerPlayFilled,
  IconSettings,
  IconBackspace,
  IconRotate,
  IconHelpCircle,
} from '@tabler/icons-react';
import {
  API,
  formatISODateTime,
  formatISODuration,
  showError,
  showSuccess,
  copyToClipboard,
} from '../../helpers/index.js';
import { useTranslation } from 'react-i18next';
import CopyModal from '../../components/CopyModal';
import './episode-image.css';

// 需要跟踪状态变化的节目状态常量（移到组件外部避免重复创建）
const ACTIVE_STATUSES = ['PENDING', 'QUEUED', 'DOWNLOADING'];

const FeedDetail = () => {
  const { t } = useTranslation();
  const isSmallScreen = useMediaQuery('(max-width: 36em)');
  const { type, feedId } = useParams();
  const navigate = useNavigate();
  const [feed, setFeed] = useState(null);
  const [originalInitialEpisodes, setOriginalInitialEpisodes] = useState(0);
  const [episodes, setEpisodes] = useState([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [hasMoreEpisodes, setHasMoreEpisodes] = useState(true);
  const [loadingEpisodes, setLoadingEpisodes] = useState(false);
  const observerRef = useRef();
  const loadingRef = useRef(false); // Use ref to track loading state without causing re-renders
  const [
    confirmDeleteFeedOpened,
    { open: openConfirmDeleteFeed, close: closeConfirmDeleteFeed },
  ] = useDisclosure(false);
  const [editConfigOpened, { open: openEditConfig, close: closeEditConfig }] = useDisclosure(false);
  const [copyModalOpened, { open: openCopyModal, close: closeCopyModal }] = useDisclosure(false);
  const [copyText, setCopyText] = useState('');
  const [refreshTimer, setRefreshTimer] = useState(null);
  const audioQualityDocUrl = 'https://github.com/aizhimou/pigeon-pod/blob/cd50eca95a2fadd12805da072e5372373093331b/documents/audio-quality-guide-en.md';

  // Intersection Observer callback for infinite scrolling
  const lastEpisodeElementRef = useCallback(
    (node) => {
      if (loadingRef.current) return;
      if (observerRef.current) observerRef.current.disconnect();
      observerRef.current = new IntersectionObserver(
        (entries) => {
          if (entries[0].isIntersecting && hasMoreEpisodes && !loadingRef.current) {
            setCurrentPage((prevPage) => prevPage + 1);
          }
        },
        { threshold: 0.1 },
      );
      if (node) observerRef.current.observe(node);
    },
    [hasMoreEpisodes],
  );

  const renderAudioQualityLabel = () => (
    <Group gap={4} align="center">
      <Text>{t('audio_quality')}</Text>
      <Tooltip label={t('audio_quality_help_tooltip')} withArrow>
        <ActionIcon
          component="a"
          href={audioQualityDocUrl || '#'}
          target="_blank"
          rel="noopener noreferrer"
          variant="subtle"
          size="sm"
          onClick={(event) => {
            if (!audioQualityDocUrl) {
              event.preventDefault();
            }
          }}
          aria-label={t('audio_quality_help_tooltip')}
        >
          <IconHelpCircle size={16} />
        </ActionIcon>
      </Tooltip>
    </Group>
  );

  const fetchFeedDetail = useCallback(async () => {
    const res = await API.get(`/api/feed/${type}/detail/${feedId}`);
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
    } else {
      setFeed(data);
      setOriginalInitialEpisodes(data.initialEpisodes);
    }
  }, [feedId, type]);

  const fetchEpisodes = useCallback(
    async (page = 1, isInitialLoad = false) => {
      // Prevent duplicate requests using ref
      if (loadingRef.current) return;

      loadingRef.current = true;
      setLoadingEpisodes(true);

      try {
        const res = await API.get(`/api/episode/list/${feedId}?page=${page}&size=10`);
        const { code, msg, data } = res.data;

        if (code !== 200) {
          showError(msg);
          return;
        }

        // MyBatis Plus Page object has 'records' for data and 'pages' for total pages
        const episodes = data.records || [];
        const totalPages = data.pages || 0;

        if (isInitialLoad) {
          setEpisodes(episodes);
          setCurrentPage(1);
        } else {
          setEpisodes((prevEpisodes) => [...prevEpisodes, ...episodes]);
        }

        // Check if there are more episodes to load
        setHasMoreEpisodes(page < totalPages);
      } catch (error) {
        showError('Failed to load episodes');
        console.error('Fetch episodes error:', error);
      } finally {
        loadingRef.current = false;
        setLoadingEpisodes(false);
      }
    },
    [feedId], // Remove loadingEpisodes dependency
  );

  useEffect(() => {
    fetchFeedDetail();
    fetchEpisodes(1, true); // Initial load
  }, [fetchFeedDetail, fetchEpisodes]);

  useEffect(() => {
    if (currentPage > 1) {
      fetchEpisodes(currentPage, false); // Load more episodes
    }
  }, [currentPage, fetchEpisodes]);

  // 组件卸载时清理定时器
  useEffect(() => {
    return () => {
      if (refreshTimer) {
        clearInterval(refreshTimer);
      }
    };
  }, [refreshTimer]);

  // Update feed config
  const updateFeedConfig = async () => {
    const res = await API.put(`/api/feed/${type}/config/${feedId}`, feed);
    const { code, msg, data } = res.data;

    if (code !== 200) {
      showError(msg || t('update_channel_config_failed'));
      return;
    }

    if (data.downloadHistory) {
      showSuccess(t('channel_config_updated_and_add_history_episodes_task_submitted'));
    } else {
      showSuccess(t('channel_config_updated'));
    }
    closeEditConfig();
  };

  const deleteFeed = async () => {
    const response = await API.delete(`/api/feed/${type}/delete/${feedId}`);
    const { code, msg } = response.data;

    if (code !== 200) {
      showError(msg || t('delete_channel_failed'));
      return;
    }

    showSuccess(t('channel_deleted_success'));

    // Navigate back to the feeds list page
    navigate('/');
  };

  const handleSubscribe = async () => {
    if (!feed) {
      return;
    }
    try {
      const response = await API.get(`/api/feed/${type}/subscribe/${feed.id}`);
      const { code, msg, data } = response.data;

      if (code !== 200) {
        showError(msg || t('failed_to_generate_subscription_url'));
        return;
      }

      // 使用自定义复制功能
      await copyToClipboard(
        data,
        () => {
          // 复制成功回调
          showSuccess(t('subscription_link_generated_success'));
        },
        (text) => {
          // 需要手动复制时的回调
          setCopyText(text);
          openCopyModal();
        },
      );
    } catch (error) {
      showError(t('failed_to_generate_subscription_url'));
      console.error('Subscribe error:', error);
    }
  };

  const getDownloadStatusColor = (status) => {
    switch (status) {
      case 'COMPLETED':
        return 'green';
      case 'DOWNLOADING':
        return 'blue';
      case 'QUEUED':
        return 'cyan';
      case 'PENDING':
        return 'yellow';
      case 'FAILED':
        return 'red';
      default:
        return 'gray';
    }
  };

  // 检查是否有需要跟踪状态变化的节目（PENDING, QUEUED, DOWNLOADING）
  const hasActiveEpisodes = useCallback(() => {
    return episodes.some(episode => ACTIVE_STATUSES.includes(episode.downloadStatus));
  }, [episodes]);

  // 刷新活跃状态节目的状态（PENDING, QUEUED, DOWNLOADING）
  const refreshActiveEpisodes = useCallback(async () => {
    if (!hasActiveEpisodes()) return;
    
    try {
      // 获取当前活跃状态的节目ID列表
      const activeIds = episodes
        .filter(episode => ACTIVE_STATUSES.includes(episode.downloadStatus))
        .map(episode => episode.id);
      
      if (activeIds.length === 0) return;
      
      // 使用专门的API端点获取特定节目的状态
      const res = await API.post('/api/episode/status', activeIds);
      const { code, data } = res.data;
      
      if (code !== 200) {
        console.error('Failed to fetch episode status');
        return;
      }
      
      // 更新对应节目的状态，保持分页不变，只更新状态相关字段
      setEpisodes(prevEpisodes => 
        prevEpisodes.map(episode => {
          const updatedEpisode = data.find(updated => updated.id === episode.id);
          if (updatedEpisode) {
            // 只更新状态相关的字段，保持其他字段不变
            return {
              ...episode,
              downloadStatus: updatedEpisode.downloadStatus,
              errorLog: updatedEpisode.errorLog
            };
          }
          return episode;
        })
      );
    } catch (error) {
      console.error('Failed to refresh active episodes:', error);
    }
  }, [episodes, hasActiveEpisodes]);

  // 自动刷新活跃状态节目的状态（PENDING, QUEUED, DOWNLOADING）
  useEffect(() => {
    let timer = null;

    // 如果有活跃状态的节目，设置3秒定时器
    if (hasActiveEpisodes()) {
      timer = setInterval(() => {
        refreshActiveEpisodes();
      }, 3000);
      
      setRefreshTimer(timer);
    } else {
      setRefreshTimer(null);
    }
    
    // 清理函数
    return () => {
      if (timer) {
        clearInterval(timer);
      }
    };
  }, [hasActiveEpisodes, refreshActiveEpisodes]);

  const handlePlay = (episode) => {
    // 新标签页打开YouTube视频链接
    let videoId = episode.id;
    let youtubeVideoUrl = `https://www.youtube.com/watch?v=${videoId}`;
    window.open(youtubeVideoUrl, '_blank', 'noopener,noreferrer');
  };

  const deleteEpisode = async (episodeId) => {
    const response = await API.delete(`/api/episode/${episodeId}`);
    const { code, msg } = response.data;

    if (code !== 200) {
      showError(msg || t('delete_episode_failed'));
      return;
    }

    showSuccess(t('episode_deleted_success'));
    await fetchEpisodes(1, true); // 重新拉取第一页
    setCurrentPage(1); // 重置分页
  };

  const retryEpisode = async (episodeId) => {
    const response = await API.post(`/api/episode/retry/${episodeId}`);
    const { code, msg } = response.data;

    if (code !== 200) {
      showError(msg || t('retry_failed'));
      return;
    }
    showSuccess(t('retry_submitted'));
    await fetchEpisodes(1, true); // 重新拉取第一页
    setCurrentPage(1); // 重置分页
  };

  if (!feed) {
    return (
      <Container>
        <Center h={400}>
          <Title order={2}>{t('loading_channel_details')}</Title>
        </Center>
      </Container>
    );
  }

  return (
    <Container size="xl" py={isSmallScreen ? 'md' : 'xl'}>
      {/* Feed Header Section */}
      <Paper withBorder radius="md" mb="lg" p={{ base: 'xs', md: 'md', lg: 'lg' }}>
        <Grid>
          {/* Left column with avatar */}
          <Grid.Col span={{ base: 4, sm: 3 }}>
            <Center>
              <Avatar
                src={feed.coverUrl}
                alt={feed.title}
                size={isSmallScreen ? 100 : 180}
                radius="md"
                component="a"
                href={feed.originalUrl}
                target="_blank"
                rel="noopener noreferrer"
                style={{ cursor: 'pointer' }}
              />
            </Center>
          </Grid.Col>

          {/* Right column with feed details */}
          <Grid.Col span={{ base: 8, sm: 9 }}>
            <Group mb={isSmallScreen ? '0' : 'sm'}>
              <Title
                order={isSmallScreen ? 3 : 2}
                component="a"
                href={feed.originalUrl}
                target="_blank"
                rel="noopener noreferrer"
                style={{ cursor: 'pointer', textDecoration: 'none', color: 'inherit' }}
              >
                {feed.title}
              </Title>
            </Group>

            <Text
              size="sm"
              lineClamp={isSmallScreen ? 2 : 4}
              style={{ minHeight: isSmallScreen ? '2rem' : '4rem' }}
            >
              {feed.description ? feed.description : t('no_description_available')}
            </Text>

            <Flex visibleFrom={'xs'} gap="md" align="flex-center" mt="lg">
              <Button
                size="xs"
                leftSection={<IconBrandApplePodcast size={16} />}
                onClick={handleSubscribe}
              >
                {t('subscribe')}
              </Button>
              <Button
                size="xs"
                color="orange"
                leftSection={<IconSettings size={16} />}
                onClick={openEditConfig}
              >
                {t('config')}
              </Button>
              <Button
                size="xs"
                color="pink"
                leftSection={<IconBackspace size={16} />}
                onClick={openConfirmDeleteFeed}
              >
                {t('delete')}
              </Button>
            </Flex>
          </Grid.Col>
        </Grid>
        {/* Buttons for tiny screens */}
        <Group hiddenFrom={'xs'} gap='xs' mt="xs" wrap="no-wrap" >
          <Button
            size="xs"
            leftSection={<IconBrandApplePodcast size={14} />}
            onClick={handleSubscribe}
          >
            {t('subscribe')}
          </Button>
          <Button
            size="xs"
            color="orange"
            leftSection={<IconSettings size={14} />}
            onClick={openEditConfig}
          >
            {t('config')}
          </Button>
          <Button
            size="xs"
            color="pink"
            leftSection={<IconBackspace size={14} />}
            onClick={openConfirmDeleteFeed}
          >
            {t('delete')}
          </Button>
        </Group>
      </Paper>

      {/* Episodes Section */}
      <Box>
        {episodes.length === 0 ? (
          <Center py="xl">
            <Text c="dimmed">{t('no_episodes_found')}</Text>
          </Center>
        ) : (
          <Stack>
            {episodes.map((episode, index) => (
              <Card
                key={episode.id}
                padding="md"
                radius="md"
                withBorder
                ref={index === episodes.length - 1 ? lastEpisodeElementRef : null}
              >
                <Grid>
                  {/* Episode thumbnail with hover and play button */}
                  <Grid.Col span={{ base: 12, sm: 3 }}>
                    <Box
                      style={{
                        position: 'relative',
                        cursor: 'pointer',
                        overflow: 'hidden',
                        borderRadius: 'var(--mantine-radius-md)',
                      }}
                      className="episode-image-container"
                    >
                      <Image
                        src={episode.maxCoverUrl || episode.defaultCoverUrl}
                        alt={episode.title}
                        radius="md"
                        height={160}
                        className="episode-image"
                        style={{ transition: 'filter 0.3s' }}
                      />
                      <Box
                        className="episode-play-overlay"
                        style={{
                          position: 'absolute',
                          top: 0,
                          left: 0,
                          width: '100%',
                          height: '100%',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          pointerEvents: 'none',
                          zIndex: 2,
                        }}
                      >
                        <Button
                          radius="xl"
                          size="lg"
                          leftSection={<IconPlayerPlayFilled size={32} />}
                          style={{
                            backdropFilter: 'blur(8px)',
                            background: 'rgba(255,255,255,0.25)',
                            boxShadow: '0 4px 32px rgba(0,0,0,0.15)',
                            pointerEvents: 'auto',
                          }}
                          onClick={() => handlePlay(episode)}
                        >
                          {t('play')}
                        </Button>
                      </Box>
                    </Box>
                  </Grid.Col>

                  {/* Episode details */}
                  <Grid.Col span={{ base: 12, sm: 9 }}>
                    <Stack>
                      <Box>
                        <Group justify="space-between">
                          <Box
                            style={{ maxWidth: isSmallScreen ? '66%' : '85%', overflow: 'hidden' }}
                          >
                            <Title
                              order={isSmallScreen ? 5 : 4}
                              style={{
                                whiteSpace: 'nowrap',
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                              }}
                              title={episode.title}
                            >
                              {episode.title}
                            </Title>
                          </Box>
                          <Text c="dimmed" style={{ whiteSpace: 'nowrap' }}>
                            {formatISODuration(episode.duration)}
                          </Text>
                        </Group>

                        <Text
                          size="sm"
                          mt="xs"
                          lineClamp={isSmallScreen ? 3 : 4}
                          style={{ minHeight: isSmallScreen ? '0' : '4rem' }}
                        >
                          {episode.description
                            ? episode.description
                            : t('no_description_available')}
                        </Text>
                      </Box>

                      <Group justify="space-between" align="center">
                        <Group>
                          <Text size="sm" c="dimmed">
                            <IconClock
                              size={14}
                              style={{
                                display: 'inline',
                                verticalAlign: 'text-bottom',
                              }}
                            />{' '}
                            {episode.publishedAt
                              ? formatISODateTime(episode.publishedAt)
                              : t('unknown_date')}
                          </Text>
                          {episode.downloadStatus && episode.downloadStatus !== 'COMPLETED' ? (
                            episode.downloadStatus === 'FAILED' ? (
                              <Tooltip
                                multiline
                                w={300}
                                withArrow
                                transitionProps={{ duration: 200 }}
                                label={episode.errorLog || t('unknown_error')}
                              >
                                <Badge
                                  variant="light"
                                  color={getDownloadStatusColor(episode.downloadStatus)}
                                >
                                  {episode.downloadStatus}
                                </Badge>
                              </Tooltip>
                            ) : (
                              <Badge
                                color={getDownloadStatusColor(episode.downloadStatus)}
                                variant="light"
                              >
                                {episode.downloadStatus}
                              </Badge>
                            )
                          ) : null}
                        </Group>
                        <Group>
                          {episode.downloadStatus === 'FAILED' ? (
                            <Button
                              size="compact-xs"
                              variant="outline"
                              color="orange"
                              onClick={() => retryEpisode(episode.id)}
                              leftSection={<IconRotate size={16} />}
                            >
                              {t('retry')}
                            </Button>
                          ) : null}
                          {episode.downloadStatus !== 'DOWNLOADING' && episode.downloadStatus !== 'QUEUED' ? (
                            <Button
                              size="compact-xs"
                              variant="outline"
                              color="pink"
                              onClick={() => deleteEpisode(episode.id)}
                              leftSection={<IconBackspace size={16} />}
                            >
                              {t('delete')}
                            </Button>
                          ) : null}
                        </Group>
                      </Group>
                    </Stack>
                  </Grid.Col>
                </Grid>
              </Card>
            ))}

            {/* Loader for infinite scrolling */}
            {loadingEpisodes && (
              <Center>
                <Loader />
              </Center>
            )}
          </Stack>
        )}
      </Box>

      {/* Delete Channel Confirmation Modal */}
      <Modal
        opened={confirmDeleteFeedOpened}
        onClose={closeConfirmDeleteFeed}
        title={t('confirm_delete_channel')}
      >
        <Text fw={500}>{t('confirm_delete_channel_tip')}</Text>
        <Group justify="flex-end" mt="md">
          <Button
            color="red"
            onClick={() => {
              deleteFeed().then(closeConfirmDeleteFeed);
            }}
          >
            {t('confirm')}
          </Button>
        </Group>
      </Modal>

      {/* Edit Channel Configuration Modal */}
      <Modal
        opened={editConfigOpened}
        onClose={closeEditConfig}
        size="lg"
        title={t('edit_channel_configuration')}
      >
        <Stack>
          <TextInput
            label={t('title_contain_keywords')}
            name="containKeywords"
            placeholder={t('multiple_keywords_space_separated')}
            value={feed?.containKeywords}
            onChange={(event) => setFeed({ ...feed, containKeywords: event.target.value })}
          />
          <TextInput
            label={t('title_exclude_keywords')}
            name="excludeKeywords"
            placeholder={t('multiple_keywords_space_separated')}
            value={feed?.excludeKeywords}
            onChange={(event) => setFeed({ ...feed, excludeKeywords: event.target.value })}
          />
          <NumberInput
            label={t('minimum_duration_minutes')}
            name="minimumDuration"
            placeholder="0"
            value={feed?.minimumDuration}
            onChange={(value) => setFeed({ ...feed, minimumDuration: value })}
          />
          <NumberInput
            label={t('initial_episodes_channel')}
            name="initialEpisodes"
            placeholder={t('3')}
            value={feed?.initialEpisodes}
            min={originalInitialEpisodes}
            clampBehavior="strict"
            onChange={(value) => setFeed({ ...feed, initialEpisodes: value })}
          />
          <NumberInput
            label={t('maximum_episodes')}
            name="maximumEpisodes"
            placeholder={t('unlimited')}
            value={feed?.maximumEpisodes}
            onChange={(value) => setFeed({ ...feed, maximumEpisodes: value })}
          />
          <NumberInput
            label={renderAudioQualityLabel()}
            description={t('audio_quality_description')}
            name="audioQuality"
            placeholder=""
            value={feed?.audioQuality}
            min={0}
            max={10}
            clampBehavior="strict"
            onChange={(value) =>
              setFeed({ ...feed, audioQuality: value === '' ? null : value })
            }
          />
          <Group mt="md" justify="flex-end">
            <Button variant="filled" onClick={updateFeedConfig}>
              {t('save_changes')}
            </Button>
          </Group>
        </Stack>
      </Modal>

      {/* Copy Modal for manual copy */}
      <CopyModal
        opened={copyModalOpened}
        onClose={closeCopyModal}
        text={copyText}
        title={t('manual_copy_title')}
      />
    </Container>
  );
};

export default FeedDetail;
