import { Pressable, StyleSheet } from 'react-native';
import { router } from 'expo-router';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useAuth } from '@/hooks/use-auth';

export default function CustomerProfileScreen() {
  const { setAuthToken } = useAuth();

  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title">Profile</ThemedText>
      <ThemedText style={styles.subtitle}>Manage your account settings.</ThemedText>
      <Pressable
        style={styles.secondaryButton}
        onPress={async () => {
          await setAuthToken(null);
          router.replace('/(auth)/country');
        }}>
        <ThemedText style={styles.secondaryButtonText}>Sign Out</ThemedText>
      </Pressable>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 24,
    gap: 12,
  },
  subtitle: {
    color: '#6b7280',
  },
  secondaryButton: {
    marginTop: 16,
    borderWidth: 1,
    borderColor: '#e5e7eb',
    paddingVertical: 12,
    alignItems: 'center',
    borderRadius: 8,
  },
  secondaryButtonText: {
    color: '#111827',
  },
});
