import { fireEvent, render } from '@testing-library/react-native';

import { ErrorState } from '@/components/feedback/error-state';

describe('ErrorState', () => {
  it('renders an accessible retry action', async () => {
    const onRetry = jest.fn();
    const screen = await render(
      <ErrorState description="Unable to load the dashboard." onRetry={onRetry} />,
    );

    expect(screen.getByText('Unable to load the dashboard.')).toBeTruthy();
    await fireEvent.press(screen.getByRole('button', { name: 'Try again' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});
