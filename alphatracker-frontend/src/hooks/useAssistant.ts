import { useContext } from 'react';
import { AssistantContext, type AssistantContextType } from '../context/assistantContextValue';

// What components call: { messages, sending, error, send, retry, clear, isOpen, open, close, toggle }.
export function useAssistant(): AssistantContextType {
  const context = useContext(AssistantContext);
  if (!context) {
    throw new Error('useAssistant must be used within an AssistantProvider');
  }
  return context;
}
