import React, { useContext, useState } from 'react';
import { API, showError, showSuccess } from '../../helpers/index.js';
import {
  Button,
  Container,
  Paper,
  Group,
  PasswordInput,
  Stack,
  TextInput,
  Title,
  Text,
  Modal,
  CopyButton,
  Tooltip,
  ActionIcon,
  Divider,
} from '@mantine/core';
import { UserContext } from '../../context/User/UserContext.jsx';
import { hasLength, useForm } from '@mantine/form';
import { useDisclosure } from '@mantine/hooks';
import {
  IconCheck,
  IconCopy,
  IconLock,
} from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';

const UserSetting = () => {
  const { t } = useTranslation();
  const [state, dispatch] = useContext(UserContext);
  const [resetPasswordLoading, setResetPasswordLoading] = useState(false);
  const [resetPasswordOpened, { open: openResetPassword, close: closeResetPassword }] =
    useDisclosure(false);
  const [
    confirmGenerateApiKeyOpened,
    { open: openConfirmGenerateApiKey, close: closeConfirmGenerateApiKey },
  ] = useDisclosure(false);
  const [changeUsernameOpened, { open: openChangeUsername, close: closeChangeUsername }] =
    useDisclosure(false);
  const [newUsername, setNewUsername] = useState('');

  // YouTube Data API Key states
  const [editYoutubeApiKeyOpened, { open: openEditYoutubeApiKey, close: closeEditYoutubeApiKey }] =
    useDisclosure(false);
  const [youtubeApiKey, setYoutubeAoiKey] = useState('');

  const resetPassword = async (values) => {
    setResetPasswordLoading(true);
    const res = await API.post('/api/account/reset-password', {
      id: state.user.id,
      password: values.oldPassword,
      newPassword: values.newPassword,
    });
    const { code, msg } = res.data;
    if (code === 200) {
      showSuccess(t('password_reset_success'));
    } else {
      showError(msg);
    }
    setResetPasswordLoading(false);
  };

  const generateApiKey = async () => {
    const res = await API.get('/api/account/generate-api-key');
    const { code, msg, data } = res.data;
    if (code === 200) {
      showSuccess(t('api_key_generated'));
      // update the apiKey in the context
      const user = {
        ...state.user,
        apiKey: data,
      };
      dispatch({
        type: 'login',
        payload: user,
      });
      localStorage.setItem('user', JSON.stringify(user));
    } else {
      showError(msg);
    }
  };

  const changeUsername = async () => {
    const res = await API.post('/api/account/change-username', {
      id: state.user.id,
      username: newUsername,
    });
    const { code, msg, data } = res.data;
    if (code === 200) {
      showSuccess(t('username_changed_success'));
      dispatch({ type: 'login', payload: data });
      localStorage.setItem('user', JSON.stringify(data));
      closeChangeUsername();
    } else {
      showError(msg);
    }
  };

  // YouTube API Key functions
  const saveYoutubeApiKey = async () => {
    const res = await API.post('/api/account/update-youtube-api-key', {
      id: state.user.id,
      youtubeApiKey: youtubeApiKey,
    });
    const { code, msg, data } = res.data;
    if (code === 200) {
      showSuccess(t('youtube_api_key_saved'));
      // update the apiKey in the context
      const user = {
        ...state.user,
        youtubeApiKey: data,
      };
      dispatch({
        type: 'login',
        payload: user,
      });
      localStorage.setItem('user', JSON.stringify(user));
      closeEditYoutubeApiKey();
    } else {
      showError(msg);
    }
  };

  const resetPasswordForm = useForm({
    mode: 'uncontrolled',
    initialValues: {
      oldPassword: '',
      newPassword: '',
    },
    validate: {
      oldPassword: hasLength({ min: 6 }, 'Old password must be at least 6 characters long'),
      newPassword: hasLength({ min: 6 }, 'New password must be at least 6 characters long'),
    },
  });

  return (
    <Container size="lg" mt="lg">
      {!state?.user ? (
        <Stack>
          <Paper shadow="xs" p="md">
            <Text c="dimmed">{t('loading')}...</Text>
          </Paper>
        </Stack>
      ) : (
      <Stack>
        <Paper shadow="xs" p="md">
          <Stack>
            <Title order={4}>{t('account_setting')}</Title>
            <Group>
              <Text c="dimmed">{t('username')}:</Text>
              <Text>{state.user?.username}</Text>
              <Button hiddenFrom='sm' fullWidth onClick={openChangeUsername}>{t('change_username')}</Button>
              <Button hiddenFrom='sm' fullWidth onClick={openResetPassword}>{t('reset_password')}</Button>
            </Group>
            <Divider hiddenFrom='sm' />
            <Group>
              <Text c="dimmed">API Key:</Text>
              <Text truncate>{state.user?.apiKey ? state.user.apiKey : t('not_set')}</Text>
              <Button onClick={openConfirmGenerateApiKey} hiddenFrom='sm' fullWidth>
                {state.user?.apiKey ? t('change_api_key') : t('generate_api_key')}
              </Button>
            </Group>
            <Divider hiddenFrom='sm' />
            <Group>
              <Text c="dimmed">YouTube API Key:</Text>
              <Text truncate>
                {state.user?.youtubeApiKey ? state.user.youtubeApiKey : t('youtube_api_key_not_set')}
              </Text>
              <Button onClick={openEditYoutubeApiKey} hiddenFrom='sm' fullWidth>
                {state.user?.youtubeApiKey ? t('edit_youtube_api_key') : t('set_youtube_api_key')}
              </Button>
            </Group>
            <Divider hiddenFrom='sm' />
            <Group mt="md" visibleFrom="sm">
              <Button onClick={openChangeUsername}>{t('change_username')}</Button>
              <Button onClick={openResetPassword}>{t('reset_password')}</Button>
              <Button onClick={openConfirmGenerateApiKey}>
                {state.user?.apiKey ? t('change_api_key') : t('generate_api_key')}
              </Button>
              <Button onClick={openEditYoutubeApiKey}>
                {state.user?.youtubeApiKey ? t('edit_youtube_api_key') : t('set_youtube_api_key')}
              </Button>
            </Group>
          </Stack>
        </Paper>
      </Stack>
      )}

      <Modal opened={resetPasswordOpened} onClose={closeResetPassword} title={t('reset_password')}>
        <form onSubmit={resetPasswordForm.onSubmit((values) => resetPassword(values))}>
          <PasswordInput
            name="oldPassword"
            label={t('old_password')}
            withAsterisk
            leftSection={<IconLock size={16} />}
            placeholder={t('enter_old_password')}
            key={resetPasswordForm.key('oldPassword')}
            {...resetPasswordForm.getInputProps('oldPassword')}
            style={{ flex: 1 }}
          />
          <PasswordInput
            mt="sm"
            name="newPassword"
            label={t('new_password')}
            withAsterisk
            leftSection={<IconLock size={16} />}
            placeholder={t('enter_new_password')}
            key={resetPasswordForm.key('newPassword')}
            {...resetPasswordForm.getInputProps('newPassword')}
            style={{ flex: 1 }}
          />
          <Group justify="flex-end" mt="sm">
            <Button mt="sm" loading={resetPasswordLoading} type="submit">
              {t('confirm_reset')}
            </Button>
          </Group>
        </form>
      </Modal>

      <Modal
        opened={confirmGenerateApiKeyOpened}
        onClose={closeConfirmGenerateApiKey}
        title={t('confirm_generation')}
      >
        <Text fw={500}>{t('confirm_generate_api_key_tip')}</Text>
        <Group justify="flex-end" mt="md">
          <Button
            color="red"
            onClick={() => {
              generateApiKey().then(closeConfirmGenerateApiKey);
            }}
          >
            {t('confirm')}
          </Button>
        </Group>
      </Modal>

      <Modal
        opened={changeUsernameOpened}
        onClose={closeChangeUsername}
        title={t('change_username')}
      >
        <TextInput
          withAsterisk
          label={t('new_username')}
          placeholder={t('enter_new_username')}
          value={newUsername}
          onChange={(event) => setNewUsername(event.currentTarget.value)}
        />
        <Group justify="flex-end" mt="md">
          <Button
            onClick={() => {
              changeUsername().then();
            }}
          >
            {t('confirm')}
          </Button>
        </Group>
      </Modal>

      {/* YouTube Data API Key Edit Modal */}
      <Modal
        opened={editYoutubeApiKeyOpened}
        onClose={closeEditYoutubeApiKey}
        title={t('youtube_data_api_key')}
      >
        <PasswordInput
          label={t('youtube_data_api_key')}
          placeholder={t('enter_youtube_data_api_key')}
          value={youtubeApiKey}
          onChange={(event) => setYoutubeAoiKey(event.currentTarget.value)}
          withAsterisk
          leftSection={<IconLock size={16} />}
        />
        <Group justify="flex-end" mt="md">
          <Button
            onClick={() => {
              saveYoutubeApiKey().then();
            }}
          >
            {t('confirm')}
          </Button>
        </Group>
      </Modal>
    </Container>
  );
};

export default UserSetting;
