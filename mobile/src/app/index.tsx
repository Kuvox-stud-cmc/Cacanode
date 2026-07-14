import { Redirect } from 'expo-router';

import { useAppSelector } from '@/store/hooks';

export default function IndexRoute() {
  const status = useAppSelector((state) => state.auth.status);
  if (status === 'authenticated') return <Redirect href="/dashboard" />;
  if (status === 'awaiting_2fa') return <Redirect href="/verify-login-2fa" />;
  return <Redirect href="/login" />;
}
