import { fireEvent, render } from '@testing-library/react-native';

import {
  getAvatarInitials,
  HeaderAvatarButton,
} from '@/features/navigation/components/header-avatar-button';

describe('HeaderAvatarButton', () => {
  it('uses first and last name initials with safe fallbacks', () => {
    expect(getAvatarInitials('Ada Lovelace')).toBe('AL');
    expect(getAvatarInitials('Prince')).toBe('PR');
    expect(getAvatarInitials(undefined, 'person@example.com')).toBe('PE');
  });

  it('exposes an accessible account-menu action', async () => {
    const onPress = jest.fn();
    const screen = await render(
      <HeaderAvatarButton fullName="Ada Lovelace" onPress={onPress} />,
    );
    expect(screen.getByText('AL')).toBeTruthy();
    await fireEvent.press(screen.getByRole('button', { name: 'Open account menu' }));
    expect(onPress).toHaveBeenCalledTimes(1);
  });
});
