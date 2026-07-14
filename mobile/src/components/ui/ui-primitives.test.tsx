import { fireEvent, render } from '@testing-library/react-native';
import { Text } from 'react-native';

import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Sheet } from '@/components/ui/sheet';
import { TextField } from '@/components/ui/text-field';

jest.mock('react-native-safe-area-context', () => ({
  useSafeAreaInsets: () => ({ bottom: 0, left: 0, right: 0, top: 0 }),
}));

describe('shared UI primitives', () => {
  it('supports disabled and loading button states', async () => {
    const onPress = jest.fn();
    const screen = await render(
      <>
        <Button disabled onPress={onPress}>Disabled action</Button>
        <Button accessibilityLabel="Saving" loading onPress={onPress}>Save</Button>
      </>,
    );
    await fireEvent.press(screen.getByRole('button', { name: 'Disabled action' }));
    await fireEvent.press(screen.getByRole('button', { name: 'Saving' }));
    expect(onPress).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Loading')).toBeTruthy();
  });

  it('labels fields and exposes validation errors', async () => {
    const screen = await render(<TextField error="Required field" label="Email" value="" />);
    expect(screen.getByLabelText('Email')).toBeTruthy();
    expect(screen.getByRole('alert')).toHaveTextContent('Required field');
  });

  it('dismisses dialogs and sheets accessibly', async () => {
    const dismissDialog = jest.fn();
    const dialog = await render(
      <Dialog onDismiss={dismissDialog} title="Confirm" visible>
        <Text>Dialog content</Text>
      </Dialog>,
    );
    await fireEvent.press(
      dialog.getByLabelText('Dismiss dialog', { includeHiddenElements: true }),
    );
    expect(dismissDialog).toHaveBeenCalledTimes(1);

    const dismissSheet = jest.fn();
    const sheet = await render(
      <Sheet onDismiss={dismissSheet} title="Account" visible>
        <Text>Sheet content</Text>
      </Sheet>,
    );
    await fireEvent.press(sheet.getAllByRole('button', { name: 'Close account menu' })[0]);
    expect(dismissSheet).toHaveBeenCalledTimes(1);
  });
});
