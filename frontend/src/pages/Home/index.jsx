import React, { useEffect, useState } from 'react';
import { API, formatISODateTime, formatISODuration, showError } from '../../helpers';
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
import { useDisclosure } from '@mantine/hooks';

const Home = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [channelUrl, setChannelUrl] = useState('');
  const [fetchChannelLoading, setFetchChannelLoading] = useState(false);
  const [filerLoading, setFilterLoading] = useState(false);
  const [addChannelLoading, setAddChannelLoading] = useState(false);
  const [channel, setChannel] = useState([]);
  const [episodes, setEpisodes] = useState([]);
  const [channels, setChannels] = useState([]);
  const [opened, { open, close }] = useDisclosure(false);
  const [editConfigOpened, { open: openEditConfig, close: closeEditConfig }] = useDisclosure(false);


  const fetchChannels = async () => {
    const res = await API.get('/api/channel/list');
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
      return;
    }
    setChannels(data);
  };

  const goToChannelDetail = (channelId) => {
    navigate(`/channel/${channelId}`);
  };

  const fetchChannel = async () => {
    if (!channelUrl) {
      showError(t('please_enter_valid_youtube_url'));
      return;
    }
    setFetchChannelLoading(true);
    const res = await API.post('/api/channel/fetch', {
      channelUrl: channelUrl,
      channelSource: 'YOUTUBE',
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

    // Add the new channel at the beginning of the channels list
    setChannels((prevChannels) => [data, ...prevChannels]);

    setAddChannelLoading(false);
    close();
  };

  const filterChannel = async () => {
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
    closeEditConfig()
  };

  useEffect(() => {
    fetchChannels().then();
  }, []);

  return (
    <Container size="lg" mt="lg">
      <Group pos="relative">
        <Input
          leftSection={<IconSearch size={16} />}
          placeholder={t('enter_youtube_channel_url')}
          name="channelUrl"
          value={channelUrl}
          onChange={(e) => setChannelUrl(decodeURIComponent(e.target.value))}
          style={{ flex: 1 }}
        />
        <Button
          onClick={fetchChannel}
          loading={fetchChannelLoading}
          variant="gradient"
          gradient={{ from: '#ae2140', to: '#f28b96', deg: 10 }}
        >
          {t('new_channel')}
        </Button>
      </Group>

      <Grid mt="xl">
        {channels.length > 0 ? (
          channels.map((channel) => (
            <Grid.Col key={channel.id} span={2}>
              <Card
                shadow="sm"
                padding="sm"
                radius="sm"
                onClick={() => goToChannelDetail(channel.id)}
                style={{ cursor: 'pointer' }}
              >
                <Card.Section>
                  <Image src={channel.avatarUrl} alt={channel.name} height={160} />
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
                  {channel.name}
                </Text>
                <Text c="dimmed" size="xs">
                  {new Date(channel.lastPublishedAt).toLocaleDateString()} {t('updated')}
                </Text>
              </Card>
            </Grid.Col>
          ))
        ) : (
          <Grid.Col span={12}>
            <Text align="center" c="dimmed" size="lg">
              {t('no_channels_available')}
            </Text>
          </Grid.Col>
        )}
      </Grid>

      <Modal
        opened={opened}
        onClose={close}
        withCloseButton
        title={t('subscription_configuration')}
        size="70%"
      >
        <Stack>
          <Paper radius="md" p="md" withBorder>
            <Grid>
              <Grid.Col span={2}>
                <Center>
                  <Avatar src={channel.avatarUrl} alt={channel.name} size={125} radius="md" />
                </Center>
              </Grid.Col>
              <Grid.Col span={10}>
                <Stack>
                  <Box>
                    <Group>
                      <Title order={3}>
                        {channel.name ? channel.name : t('no_channel_name_available')}
                      </Title>
                      <Button
                        size="compact-sm"
                        color="orange"
                        leftSection={<IconSettings size={16} />}
                        onClick={openEditConfig}
                      >
                        {t('config')}
                      </Button>
                      <Button
                        size="compact-sm"
                        loading={addChannelLoading}
                        onClick={addChannel}
                        leftSection={<IconCheck size={16} />}
                      >
                        {t('confirm')}
                      </Button>
                    </Group>
                    <Text mt="xs" size="sm" lineClamp={4}>
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
                        />
                      </Grid.Col>

                      {/* Episode details */}
                      <Grid.Col span={{ base: 12, sm: 9 }}>
                        <Stack>
                          <Box>
                            <Group justify="space-between">
                              <Box style={{ maxWidth: '80%', overflow: 'hidden' }}>
                                <Title
                                  order={5}
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
                            <Text size="sm" mt="xs" lineClamp={3} style={{ minHeight: '3rem' }}>
                              {episode.description
                                ? episode.description
                                : t('no_description_available')}
                            </Text>
                          </Box>
                          <Group mt="auto">
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
                          </Group>
                        </Stack>
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
            <Button variant="filled" loading={filerLoading} onClick={filterChannel}>
              {t('confirm')}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Container>
  );
};

export default Home;
