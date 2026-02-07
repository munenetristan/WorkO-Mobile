import { Pressable, StyleSheet } from 'react-native';
import { router } from 'expo-router';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';

export default function CustomerHomeScreen() {
  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title" style={styles.title}>
        Welcome to WorkO
      </ThemedText>
      <ThemedText style={styles.subtitle}>
        Request trusted providers near you in minutes.
      </ThemedText>
      <Pressable style={styles.primaryButton} onPress={() => router.push('/services')}>
        <ThemedText style={styles.primaryButtonText}>Request Service</ThemedText>
      </Pressable>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 24,
    gap: 16,
  },
  title: {
    fontSize: 28,
  },
  subtitle: {
    color: '#6b7280',
    fontSize: 16,
  },
  primaryButton: {
    marginTop: 12,
    backgroundColor: '#2563eb',
    paddingVertical: 16,
    alignItems: 'center',
    borderRadius: 8,
  },
  primaryButtonText: {
    color: '#fff',
    fontSize: 16,
  },
});
