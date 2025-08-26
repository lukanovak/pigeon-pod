import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
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
} from '@mantine/core';
import { useClipboard, useDisclosure } from '@mantine/hooks';
import {
  IconBrandApplePodcast,
  IconClock,
  IconBrandYoutubeFilled,
  IconSquareRoundedX,
  IconPlayerPlayFilled,
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
  const [
    confirmDeleteChannelOpened,
    { open: openConfirmDeleteChannel, close: closeConfirmDeleteChannel },
  ] = useDisclosure(false);

  useEffect(() => {
    fetchChannelDetail();
    fetchPrograms();
  }, [channelId]);

  const fetchChannelDetail = async () => {
    const res = await API.get(`/api/channel/detail/${channelId}`);
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
    } else {
      setChannel(data);
    }
  };

  const fetchPrograms = async () => {
    const res = await API.get(`/api/program/list/${channelId}`);
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
    } else {
      setEpisodes(data);
    }
  };

  const deleteChannel = async () => {
    const response = await API.delete(`/api/channel/delete/${channelId}`);
    const { code, msg } = response.data;

    if (code !== 200) {
      showError(msg || '删除频道失败');
      return;
    }

    showSuccess(`频道 ${channel.name} 已被删除`);

    // Navigate back to the channels list page
    navigate('/');
  };

  const handleSubscribe = async () => {
    try {
      const response = await API.get(`/api/channel/subscribe?handler=${channel.handler}`);
      const { code, msg, data } = response.data;

      if (code !== 200) {
        showError(msg || 'Failed to generate subscription URL');
        return;
      }

      // Copy the RSS feed URL to clipboard
      clipboard.copy(data);
      showSuccess('订阅链接生成成功，请在任意podcast客户端中使用该链接订阅频道。');
    } catch (error) {
      showError('Failed to generate subscription URL');
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
    // TODO: Implement player logic
  };

  if (!channel) {
    return (
      <Container>
        <Center h={400}>
          <Text>Loading channel details...</Text>
        </Center>
      </Container>
    );
  }

  return (
    <Container size="xl" py="xl">
      {/* Channel Header Section */}
      <Paper radius="md" p="xl" mb="xl" withBorder>
        <Grid>
          {/* Left column with avatar */}
          <Grid.Col span={{ base: 12, md: 3 }}>
            <Center>
              <Avatar src={channel.avatarUrl} alt={channel.name} size={250} radius="md" />
            </Center>
          </Grid.Col>

          {/* Right column with channel details */}
          <Grid.Col span={{ base: 12, md: 9 }}>
            <Stack>
              <Box>
                <Badge
                  component="a"
                  href={channel.channelUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  variant="light"
                  color="#ff0033"
                  mb="sm"
                  leftSection={<IconBrandYoutubeFilled size={16} />}
                  style={{ cursor: 'pointer' }}
                >
                  @{channel.handler}
                </Badge>
                <Title order={1}>{channel.name}</Title>
                <Text mt="md" size="sm" lineClamp={4} style={{ minHeight: '5rem' }}>
                  {channel.description ? channel.description : 'No description available.'}
                </Text>
              </Box>

              <Flex gap="md" wrap="wrap" align="flex-center" mt="md">
                <Button
                  size="xs"
                  leftSection={<IconBrandApplePodcast size={16} />}
                  onClick={handleSubscribe}
                  variant="filled"
                >
                  Subscribe
                </Button>
                <Button
                  size="xs"
                  color="red"
                  leftSection={<IconSquareRoundedX size={16} />}
                  onClick={openConfirmDeleteChannel}
                >
                  Delete
                </Button>
              </Flex>
            </Stack>
          </Grid.Col>
        </Grid>
      </Paper>

      {/* Episodes Section */}
      <Box>
        <Title order={2} mb="md">
          Episodes
        </Title>

        {episodes.length === 0 ? (
          <Center py="xl">
            <Text c="dimmed">No episodes found for this channel</Text>
          </Center>
        ) : (
          <Stack>
            {episodes.map((episode) => (
              <Card key={episode.id} padding="md" radius="md" withBorder>
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
                          Play
                        </Button>
                      </Box>
                    </Box>
                  </Grid.Col>

                  {/* Episode details */}
                  <Grid.Col span={{ base: 12, sm: 9 }}>
                    <Stack>
                      <Box>
                        <Group justify="space-between">
                          <Group>
                            <Title order={4}>{episode.title}</Title>
                          </Group>
                          <Text c="dimmed">{formatISODuration(episode.duration)}</Text>
                        </Group>
                        <Text size="sm" mt="xs" lineClamp={4} style={{ minHeight: '5rem' }}>
                          {episode.description ? episode.description : 'No description available.'}
                        </Text>
                      </Box>

                      <Group mt="auto">
                        <Text size="sm" c="dimmed">
                          <IconClock
                            size={14}
                            style={{ display: 'inline', verticalAlign: 'text-bottom' }}
                          />{' '}
                          {episode.publishedAt
                            ? formatISODateTime(episode.publishedAt)
                            : 'Unknown date'}
                        </Text>
                        <Badge
                          color={getDownloadStatusColor(episode.downloadStatus)}
                          variant="light"
                        >
                          {episode.downloadStatus}
                        </Badge>
                      </Group>
                    </Stack>
                  </Grid.Col>
                </Grid>
              </Card>
            ))}
          </Stack>
        )}
      </Box>

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
    </Container>
  );
};

export default ChannelDetail;
