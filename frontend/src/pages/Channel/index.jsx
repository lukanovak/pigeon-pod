import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useClipboard, useDisclosure } from '@mantine/hooks';
import {
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
} from '@mantine/core';
import {
  IconBrandApplePodcast,
  IconClock,
  IconBrandYoutubeFilled,
  IconPlayerPlayFilled,
  IconSettings,
  IconBackspace,
  IconRotate,
} from '@tabler/icons-react';
import {
  API,
  formatISODateTime,
  formatISODuration,
  showError,
  showSuccess,
} from '../../helpers/index.js';
import { useTranslation } from 'react-i18next';
import './episode-image.css';

const ChannelDetail = () => {
  const { t } = useTranslation();
  const { channelId } = useParams();
  const navigate = useNavigate();
  const clipboard = useClipboard();
  const [channel, setChannel] = useState(null);
  const [episodes, setEpisodes] = useState([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [hasMoreEpisodes, setHasMoreEpisodes] = useState(true);
  const [loadingEpisodes, setLoadingEpisodes] = useState(false);
  const observerRef = useRef();
  const loadingRef = useRef(false); // Use ref to track loading state without causing re-renders
  const [
    confirmDeleteChannelOpened,
    { open: openConfirmDeleteChannel, close: closeConfirmDeleteChannel },
  ] = useDisclosure(false);
  const [editConfigOpened, { open: openEditConfig, close: closeEditConfig }] = useDisclosure(false);

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

  const fetchChannelDetail = useCallback(async () => {
    const res = await API.get(`/api/channel/detail/${channelId}`);
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
    } else {
      setChannel(data);
    }
  }, [channelId]);

  const fetchEpisodes = useCallback(
    async (page = 1, isInitialLoad = false) => {
      // Prevent duplicate requests using ref
      if (loadingRef.current) return;

      loadingRef.current = true;
      setLoadingEpisodes(true);

      try {
        const res = await API.get(`/api/episode/list/${channelId}?page=${page}&size=10`);
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
    [channelId], // Remove loadingEpisodes dependency
  );

  useEffect(() => {
    fetchChannelDetail();
    fetchEpisodes(1, true); // Initial load
  }, [fetchChannelDetail, fetchEpisodes]);

  useEffect(() => {
    if (currentPage > 1) {
      fetchEpisodes(currentPage, false); // Load more episodes
    }
  }, [currentPage, fetchEpisodes]);

  // Update channel config
  const updateChannelConfig = async () => {
    const res = await API.put(`/api/channel/config/${channelId}`, channel);
    const { code, msg } = res.data;

    if (code !== 200) {
      showError(msg || t('update_channel_config_failed'));
      return;
    }

    showSuccess(t('channel_config_updated'));
    closeEditConfig();
  };

  const deleteChannel = async () => {
    const response = await API.delete(`/api/channel/delete/${channelId}`);
    const { code, msg } = response.data;

    if (code !== 200) {
      showError(msg || t('delete_channel_failed'));
      return;
    }

    showSuccess(t('channel_deleted_success', { name: channel.name }));

    // Navigate back to the channels list page
    navigate('/');
  };

  const handleSubscribe = async () => {
    try {
      const response = await API.get(`/api/channel/subscribe?handler=${channel.handler}`);
      const { code, msg, data } = response.data;

      if (code !== 200) {
        showError(msg || t('failed_to_generate_subscription_url'));
        return;
      }

      // Copy the RSS feed URL to clipboard
      clipboard.copy(data);
      showSuccess(t('subscription_link_generated_success'));
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
      case 'PENDING':
        return 'yellow';
      case 'FAILED':
        return 'red';
      default:
        return 'gray';
    }
  };

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
      showError(msg || '删除单集失败');
      return;
    }

    showSuccess(`单集已被删除`);
    await fetchEpisodes(1, true); // 重新拉取第一页
    setCurrentPage(1); // 重置分页
  };

  const retryEpisode = async (episodeId) => {
    const response = await API.post(`/api/episode/retry/${episodeId}`);
    const { code, msg } = response.data;

    if (code !== 200) {
      showError(msg || '重试失败');
      return;
    }
    showSuccess(`重试已提交`);
    await fetchEpisodes(1, true); // 重新拉取第一页
    setCurrentPage(1); // 重置分页
  };

  if (!channel) {
    return (
      <Container>
        <Center h={400}>
          <Title order={2}>{t('loading_channel_details')}</Title>
        </Center>
      </Container>
    );
  }

  return (
    <Container size="xl" py="xl">
      {/* Channel Header Section */}
      <Paper radius="md" p="lg" mb="lg" withBorder>
        <Grid>
          {/* Left column with avatar */}
          <Grid.Col span={{ base: 12, md: 3 }}>
            <Center>
              <Avatar src={channel.avatarUrl} alt={channel.name} size={200} radius="md" />
            </Center>
          </Grid.Col>

          {/* Right column with channel details */}
          <Grid.Col span={{ base: 12, md: 9 }}>
            <Stack>
              <Group>
                <Title order={2}>{channel.name}</Title>
                <Badge
                  component="a"
                  href={channel.channelUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  variant="light"
                  color="#ff0033"
                  size="lg"
                  leftSection={<IconBrandYoutubeFilled size={16} />}
                  style={{ cursor: 'pointer' }}
                >
                  @{channel.handler}
                </Badge>
              </Group>

              <Text size="sm" lineClamp={4} style={{ minHeight: '4rem' }}>
                {channel.description ? channel.description : t('no_description_available')}
              </Text>

              <Flex gap="md" wrap="wrap" align="flex-center" mt="sm">
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
                  onClick={openConfirmDeleteChannel}
                >
                  {t('delete')}
                </Button>
              </Flex>
            </Stack>
          </Grid.Col>
        </Grid>
      </Paper>

      {/* Episodes Section */}
      <Box>
        <Title order={2} mb="md">
          {t('episodes')}
        </Title>

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
                          <Box style={{ maxWidth: '85%', overflow: 'hidden' }}>
                            <Title
                              order={4}
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
                          <Text c="dimmed" style={{ marginLeft: 5, whiteSpace: 'nowrap' }}>
                            {formatISODuration(episode.duration)}
                          </Text>
                        </Group>

                        <Text size="sm" mt="xs" lineClamp={4} style={{ minHeight: '5rem' }}>
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
                            <Badge
                              color={getDownloadStatusColor(episode.downloadStatus)}
                              variant="light"
                            >
                              {episode.downloadStatus}
                            </Badge>
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
                          {episode.downloadStatus !== 'DOWNLOADING' ? (
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
        opened={confirmDeleteChannelOpened}
        onClose={closeConfirmDeleteChannel}
        title={t('confirm_delete_channel')}
      >
        <Text fw={500}>{t('confirm_delete_channel_tip')}</Text>
        <Group justify="flex-end" mt="md">
          <Button
            color="red"
            onClick={() => {
              deleteChannel().then(closeConfirmDeleteChannel);
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
        title={t('edit_channel_configuration')}
      >
        <Stack>
          <TextInput
            label={t('title_contain_keywords')}
            name="containKeywords"
            placeholder={t('multiple_keywords_space_separated')}
            value={channel.containKeywords}
            onChange={(event) => setChannel({ ...channel, containKeywords: event.target.value })}
          />
          <TextInput
            label={t('title_exclude_keywords')}
            name="excludeKeywords"
            placeholder={t('multiple_keywords_space_separated')}
            value={channel.excludeKeywords}
            onChange={(event) => setChannel({ ...channel, excludeKeywords: event.target.value })}
          />
          <NumberInput
            label={t('minimum_duration_minutes')}
            name="minimumDuration"
            placeholder="0"
            value={channel.minimumDuration}
            onChange={(value) => setChannel({ ...channel, minimumDuration: value })}
          />
          <NumberInput
            label={t('maximum_episodes')}
            name="maximumEpisodes"
            placeholder={t('unlimited')}
            value={channel.maximumEpisodes}
            onChange={(value) => setChannel({ ...channel, maximumEpisodes: value })}
          />
          <Group mt="md" justify="flex-end">
            <Button variant="filled" onClick={updateChannelConfig}>
              {t('save_changes')}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Container>
  );
};

export default ChannelDetail;
