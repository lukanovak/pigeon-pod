import React, { useEffect, useState } from 'react';
import { API, showError } from '../../helpers';
import { Button, Card, Container, Grid, Group, Input, Image, Text } from '@mantine/core';
import { useTranslation } from 'react-i18next';
import { IconSearch } from '@tabler/icons-react';
import { useNavigate } from 'react-router-dom';

const Home = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [channelUrl, setChannelUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [channels, setChannels] = useState([]);

  const addChannel = async () => {
    setLoading(true);
    const channel = { channelUrl: channelUrl, channelSource: 'YOUTUBE' };
    const res = await API.post('/api/channel/add', channel);
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
      setLoading(false);
      return;
    }

    // Add the new channel at the beginning of the channels list
    setChannels((prevChannels) => [data, ...prevChannels]);
    setChannelUrl(''); // Clear the input field after successful addition
    setLoading(false);
  };

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

  useEffect(() => {
    fetchChannels().then();
  }, []);

  return (
    <Container size="lg" mt="lg">
      <Group pos="relative">
        <Input
          leftSection={<IconSearch size={16} />}
          placeholder={t('enter Youtueb channel url.')}
          name="channelUrl"
          value={channelUrl}
          onChange={(e) => setChannelUrl(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              addChannel().then();
            }
          }}
          style={{ flex: 1 }}
        />
        <Button onClick={addChannel} loading={loading} variant="gradient" gradient={{ from: '#ae2140', to: '#f28b96', deg: 10 }}>
          {t('new channel')}
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
                  size='sm'
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
                  {new Date(channel.lastPublishedAt).toLocaleDateString()} 更新
                </Text>
              </Card>
            </Grid.Col>
          ))
        ) : (
          <Grid.Col span={12}>
            <Text align="center" c="dimmed" size="lg">
              暂无频道，请添加新频道
            </Text>
          </Grid.Col>
        )}
      </Grid>
    </Container>
  );
};

export default Home;
