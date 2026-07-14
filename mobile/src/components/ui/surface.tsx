import type { ComponentProps } from 'react';

import { Card } from '@/components/ui/card';

/** @deprecated Use Card. Kept as a compatibility alias for Phase 1 feedback components. */
export function Surface(props: ComponentProps<typeof Card>) {
  return <Card elevated {...props} />;
}
