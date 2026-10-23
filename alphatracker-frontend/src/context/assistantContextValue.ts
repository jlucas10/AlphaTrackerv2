import { createContext } from 'react';
import type { AssistantChat } from '../hooks/useAssistantChat';

export interface AssistantContextType extends AssistantChat {
  isOpen: boolean;
  open: () => void;
  close: () => void;
  toggle: () => void;
}

// Kept apart from the Provider component (AssistantContext.tsx) because a file that exports both a
// component and other values breaks React's Fast Refresh in development. Read it through
// hooks/useAssistant.ts rather than directly.
export const AssistantContext = createContext<AssistantContextType | undefined>(undefined);
