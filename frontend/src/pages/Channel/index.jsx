import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import {
  Container,
  Grid,
  Title,
  Text,
  Image,
  Button,
  Group,
  Card,
  Select,
  Center,
  Divider,
  Stack,
  Flex,
  Badge,
  Box,
  Paper,
  Avatar, Anchor
} from '@mantine/core';
import {
  IconBrandApplePodcast,
  IconRefresh,
  IconClock,
  IconBrandYoutubeFilled,
  IconHttpDelete,
  IconX,
  IconSquareRoundedX,
  IconUserMinus,
} from '@tabler/icons-react';
import { API, formatISODateTime, formatISODuration, showError } from '../../helpers/index.js';

const ChannelDetail = () => {
  const { channelId } = useParams();
  const [channel, setChannel] = useState(null);
  const [episodes, setEpisodes] = useState([]);
  const [updateFrequency, setUpdateFrequency] = useState('24');

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

  const handleRefreshChannel = async () => {
    const res = await API.get(`/api/program/refresh-manually/${channelId}`);
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
    } else {
      setEpisodes(data);
    }
  };

  const handleDeleteChannel = async () => {};

  const handleSubscribe = async () => {};

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
      <Paper radius="md" p="xl" mb="xl" withBorder shadow="sm">
        <Grid>
          {/* Left column with avatar */}
          <Grid.Col span={{ base: 12, md: 3 }}>
            <Center>
              <Avatar
                src={channel.avatarUrl}
                alt={channel.name}
                size={250}
                radius="md"
              />
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
                  leftSection={<IconBrandYoutubeFilled size={16} />}
                  style={{ cursor: 'pointer' }}
                >
                  {channel.handler}@
                </Badge>
                <Title order={1}>{channel.name}</Title>
                <Text mt="md" size="sm" lineClamp={4}>{channel.description}</Text>
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
                  leftSection={<IconUserMinus size={16} />}
                  onClick={handleDeleteChannel}
                >
                  Delete
                </Button>

                <Select
                  size="xs"
                  value={updateFrequency}
                  onChange={setUpdateFrequency}
                  style={{ width: 250 }}
                  data={[
                    { value: '1', label: 'Auto-Refresh Every 1 hour' },
                    { value: '8', label: 'Auto-Refresh Every 8 hours' },
                    { value: '24', label: 'Auto-Refresh Every 24 hours' },
                  ]}
                />
              </Flex>
            </Stack>
          </Grid.Col>
        </Grid>
      </Paper>

      {/* Episodes Section */}
      <Box>
        <Title order={2} mb="md">Episodes</Title>

        {episodes.length === 0 ? (
          <Center py="xl">
            <Text c="dimmed">No episodes found for this channel</Text>
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
                      height={160}
                    />
                  </Grid.Col>

                  {/* Episode details */}
                  <Grid.Col span={{ base: 12, sm: 9 }}>
                    <Stack>
                      <Box>
                        <Group justify="space-between">
                          <Group>
                            <Badge>#{episode.position}</Badge>
                            <Title order={4}>{episode.title}</Title>
                          </Group>
                          <Text c="dimmed">{formatISODuration(episode.duration)}</Text>
                        </Group>
                        <Text size="sm" mt="xs" lineClamp={4}>{episode.description}</Text>
                      </Box>

                      <Group mt="auto">
                        <Text size="sm" c="dimmed">
                          <IconClock size={14} style={{ display: 'inline', verticalAlign: 'text-bottom' }} />
                          {' '}
                          {episode.publishedAt ? formatISODateTime(episode.publishedAt) : 'Unknown date'}
                        </Text>
                        <Badge color="pink" variant="light">{episode.downloadStatus}</Badge>
                      </Group>
                    </Stack>
                  </Grid.Col>
                </Grid>
              </Card>
            ))}
          </Stack>
        )}
      </Box>
    </Container>
  );
};

export default ChannelDetail;
