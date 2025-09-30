import React, { useEffect, useState } from 'react';
import {
  API,
  formatISODateTime,
  formatISODuration,
  showError,
  showSuccess,
} from '../../helpers';
import {
  Container,
  Button,
  Card,
  Grid,
  Group,
  Input,
  Image,
  Text,
  Modal,
  Stack,
  Center,
  Avatar,
  Box,
  Title,
  Paper,
  TextInput,
  NumberInput,
} from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { IconCheck, IconClock, IconSearch, IconSettings } from '@tabler/icons-react';
import { useNavigate } from 'react-router-dom';
import { useDisclosure, useMediaQuery } from '@mantine/hooks';
import VersionUpdateAlert from '../../components/VersionUpdateAlert';

const Home = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const isSmallScreen = useMediaQuery('(max-width: 36em)');
  const [channelUrl, setChannelUrl] = useState('');
  const [fetchChannelLoading, setFetchChannelLoading] = useState(false);
  const [filerLoading, setFilterLoading] = useState(false);
  const [addChannelLoading, setAddChannelLoading] = useState(false);
  const [channel, setChannel] = useState([]);
  const [episodes, setEpisodes] = useState([]);
  const [feeds, setFeeds] = useState([]);
  const [preview, setPreview] = useState(false);
  const [opened, { open, close }] = useDisclosure(false);
  const [editConfigOpened, { open: openEditConfig, close: closeEditConfig }] = useDisclosure(false);

  const fetchFeeds = async () => {
    const res = await API.get('/api/feed/list');
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
      return;
    }
    setFeeds(data);
  };

  const goToChannelDetail = (type, channelId) => {
    navigate(`/${type}/${channelId}`);
  };

  const fetchChannel = async () => {
    if (!channelUrl) {
      showError(t('please_enter_valid_youtube_url'));
      return;
    }
    setFetchChannelLoading(true);
    const res = await API.post('/api/channel/fetch', {
      channelUrl: channelUrl.trim()
    });
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
      setFetchChannelLoading(false);
      return;
    }

    open();

    setChannel(data.channel);
    setEpisodes(data.episodes);

    setFetchChannelLoading(false);
    setChannelUrl(''); // Clear the input field after successful addition
  };

  const addChannel = async () => {
    setAddChannelLoading(true);
    const res = await API.post('/api/channel/add', channel);
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
      setAddChannelLoading(false);
      return;
    }

    showSuccess(data.message);

    // Add the new channel at the beginning of the feeds list
    setFeeds((prevChannels) => [data.channel, ...prevChannels]);

    setAddChannelLoading(false);
    close();
  };

  const filterChannel = async () => {
    if (!preview) {
      closeEditConfig();
      return;
    }
    setFilterLoading(true);
    const res = await API.post('/api/channel/preview', channel);
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
      setFilterLoading(false);
      return;
    }
    setEpisodes(data);
    setFilterLoading(false);
    closeEditConfig();
  };

  useEffect(() => {
    fetchFeeds().then();
  }, []);

  return (
    <Container size="lg" mt="lg">
      <VersionUpdateAlert />
      <Group pos="relative" wrap="wrap" gap="sm">
        <Input
          leftSection={<IconSearch size={16} />}
          placeholder={t('enter_feed_source_url')}
          name="channelUrl"
          value={channelUrl}
          onChange={(e) => setChannelUrl(decodeURIComponent(e.target.value))}
          style={{ flex: 1, minWidth: isSmallScreen ? '100%' : 0 }}
        />
        <Button
          onClick={fetchChannel}
          loading={fetchChannelLoading}
          variant="gradient"
          gradient={{ from: '#ae2140', to: '#f28b96', deg: 10 }}
          fullWidth={isSmallScreen}
        >
          {t('new_channel')}
        </Button>
      </Group>

      <Grid mt={isSmallScreen ? 'md' : 'lg'}>
        {feeds.length > 0 ? (
          feeds.map((feed) => (
            <Grid.Col key={feed.id} span={{ base: 6, xs: 4, sm: 3, md: 2, lg: 2, xl: 2 }}>
              <Card
                shadow="sm"
                padding="sm"
                radius="sm"
                onClick={() => goToChannelDetail(feed.type, feed.id)}
                style={{ cursor: 'pointer' }}
              >
                <Card.Section>
                  <Image
                    src={feed.coverUrl}
                    alt={feed.name}
                    height={isSmallScreen ? 140 : 160}
                    w="100%"
                    fit="cover"
                  />
                </Card.Section>
                <Text
                  fw={500}
                  mt="sm"
                  size="sm"
                  style={{
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    display: 'block',
                  }}
                >
                  {feed.title}
                </Text>
                <Text c="dimmed" size="xs">
                  {new Date(feed.lastPublishedAt).toLocaleDateString()} {t('updated')}
                </Text>
              </Card>
            </Grid.Col>
          ))
        ) : (
          <Grid.Col span={12}>
            <Text align="center" c="dimmed" size="lg">
              {t('no_feeds_available')}
            </Text>
          </Grid.Col>
        )}
      </Grid>

      <Modal
        opened={opened}
        onClose={close}
        withCloseButton
        title={t('subscription_configuration')}
        size={isSmallScreen ? '100%' : '70%'}
        fullScreen={isSmallScreen}
      >
        <Stack>
          <Paper radius="md" p="md" withBorder>
            <Grid>
              <Grid.Col span={{ base: 12, sm: 3 }}>
                <Center>
                  <Avatar src={channel.avatarUrl} alt={channel.name} size={125} radius="md" />
                </Center>
              </Grid.Col>
              <Grid.Col span={{ base: 12, sm: 9 }}>
                <Stack>
                  <Box>
                    <Group wrap="wrap" gap="xs">
                      <Title order={isSmallScreen ? 3 : 2}>
                        {channel.name ? channel.name : t('no_channel_name_available')}
                      </Title>
                      <Button
                        size={isSmallScreen ? 'compact-xs' : 'xs'}
                        color="orange"
                        leftSection={<IconSettings size={16} />}
                        onClick={openEditConfig}
                      >
                        {t('config')}
                      </Button>
                      <Button
                        size={isSmallScreen ? 'compact-xs' : 'xs'}
                        loading={addChannelLoading}
                        onClick={addChannel}
                        leftSection={<IconCheck size={16} />}
                      >
                        {t('confirm')}
                      </Button>
                    </Group>
                    <Text mt="xs" size="sm" lineClamp={isSmallScreen ? 3 : 4}>
                      {channel.description ? channel.description : t('no_description_available')}
                    </Text>
                  </Box>
                </Stack>
              </Grid.Col>
            </Grid>
          </Paper>

          <Box>
            {episodes.length === 0 ? (
              <Center py="xl">
                <Text c="dimmed">{t('no_episodes_found')}</Text>
              </Center>
            ) : (
              <Stack>
                {episodes.map((episode) => (
                  <Card key={episode.id} padding="md" radius="md" withBorder>
                    <Grid>
                      {/* Episode thumbnail */}
                      <Grid.Col span={{ base: 12, sm: 3 }}>
                        <Image
                          src={episode.maxCoverUrl || episode.defaultCoverUrl}
                          alt={episode.title}
                          radius="md"
                          w="100%"
                          fit="cover"
                        />
                      </Grid.Col>

                      {/* Episode details */}
                      <Grid.Col span={{ base: 12, sm: 9 }}>
                        <Text
                          fw={700}
                          style={{
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                          }}
                          title={episode.title}
                        >
                          {episode.title}
                        </Text>
                        <Text
                          size="sm"
                          lineClamp={isSmallScreen ? 2 : 4}
                          style={{ minHeight: isSmallScreen ? '2rem' : '4rem' }}
                        >
                          {episode.description
                            ? episode.description
                            : t('no_description_available')}
                        </Text>
                        <Group mt="xs" justify="space-between">
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
                          <Text c="dimmed" size="sm">
                            {formatISODuration(episode.duration)}
                          </Text>
                        </Group>
                      </Grid.Col>
                    </Grid>
                  </Card>
                ))}
              </Stack>
            )}
          </Box>
        </Stack>
      </Modal>
      {/* Edit Channel Configuration Modal */}
      <Modal
        opened={editConfigOpened}
        onClose={closeEditConfig}
        title={t('edit_channel_configuration')}
        size={isSmallScreen ? '100%' : 'md'}
        fullScreen={isSmallScreen}
      >
        <Stack>
          <TextInput
            label={t('title_contain_keywords')}
            name="containKeywords"
            placeholder={t('multiple_keywords_space_separated')}
            value={channel.containKeywords}
            onChange={(event) => {
              setChannel({ ...channel, containKeywords: event.target.value });
              setPreview(true);
            }}
          />
          <TextInput
            label={t('title_exclude_keywords')}
            name="excludeKeywords"
            placeholder={t('multiple_keywords_space_separated')}
            value={channel.excludeKeywords}
            onChange={(event) => {
              setChannel({ ...channel, excludeKeywords: event.target.value });
              setPreview(true);
            }}
          />
          <NumberInput
            label={t('minimum_duration_minutes')}
            name="minimumDuration"
            placeholder="0"
            value={channel.minimumDuration}
            onChange={(value) => {
              setChannel({ ...channel, minimumDuration: value });
              setPreview(true);
            }}
          />
          <NumberInput
            label={t('initial_episodes')}
            name="initialEpisodes"
            placeholder={t('3')}
            value={channel.initialEpisodes}
            onChange={(value) => setChannel({ ...channel, initialEpisodes: value })}
          />
          <NumberInput
            label={t('maximum_episodes')}
            name="maximumEpisodes"
            placeholder={t('unlimited')}
            value={channel.maximumEpisodes}
            onChange={(value) => setChannel({ ...channel, maximumEpisodes: value })}
          />
          <Group mt="md" justify={isSmallScreen ? 'stretch' : 'flex-end'}>
            <Button
              variant="filled"
              loading={filerLoading}
              onClick={filterChannel}
              fullWidth={isSmallScreen}
            >
              {t('confirm')}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Container>
  );
};

export default Home;
