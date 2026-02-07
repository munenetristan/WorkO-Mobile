import { StyleSheet } from 'react-native';
import { Link } from 'expo-router';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';

export default function HomeScreen() {
  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title" style={styles.title}>
        WorkO
      </ThemedText>
      <ThemedText style={styles.subtitle}>
        Global piece-job marketplace. Request services in just a few taps.
      </ThemedText>

      <Link href="/services" style={styles.primaryButton}>
        <ThemedText type="defaultSemiBold" style={styles.primaryButtonText}>
          Request Service
        </ThemedText>
      </Link>
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
    fontSize: 32,
  },
  subtitle: {
    fontSize: 16,
  },
  primaryButton: {
    marginTop: 12,
    backgroundColor: '#2563eb',
    paddingVertical: 18,
    paddingHorizontal: 20,
    alignItems: 'center',
    borderRadius: 0,
  },
  primaryButtonText: {
    color: '#fff',
    fontSize: 16,
  },
});
