import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { router } from 'expo-router';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';

type Service = {
  id: string;
  name: string;
};

export default function HomeScreen() {
  const baseUrl = process.env.EXPO_PUBLIC_API_URL;
  const [services, setServices] = useState<Service[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchServices = async () => {
      if (!baseUrl) {
        setError('EXPO_PUBLIC_API_URL is not set.');
        return;
      }
      setLoading(true);
      setError('');
      try {
        const response = await fetch(`${baseUrl}/services`);
        const data = await response.json();
        if (!response.ok) {
          throw new Error(data?.message ?? 'Failed to fetch services.');
        }
        const list = Array.isArray(data) ? data : data.services ?? [];
        setServices(list);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to fetch services.');
      } finally {
        setLoading(false);
      }
    };

    fetchServices();
  }, [baseUrl]);

  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title">WorkO Home</ThemedText>
      <ThemedText style={styles.label}>API Base URL</ThemedText>
      <ThemedText style={styles.value}>{baseUrl ?? 'Not set'}</ThemedText>

      <View style={styles.card}>
        <ThemedText type="defaultSemiBold">Service Catalog Preview</ThemedText>
        {loading ? <ThemedText>Loading services...</ThemedText> : null}
        {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
        {!loading && !error ? (
          <View style={styles.serviceList}>
            <ThemedText>
              Total services: <ThemedText type="defaultSemiBold">{services.length}</ThemedText>
            </ThemedText>
            {services.slice(0, 10).map((service) => (
              <ThemedText key={service.id} style={styles.serviceItem}>
                • {service.name}
              </ThemedText>
            ))}
          </View>
        ) : null}
      </View>

      <Pressable style={styles.primaryButton} onPress={() => router.push('/(auth)/country')}>
        <ThemedText style={styles.primaryButtonText}>Continue to Auth</ThemedText>
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
  label: {
    color: '#6b7280',
    textTransform: 'uppercase',
    fontSize: 12,
    letterSpacing: 1,
  },
  value: {
    fontSize: 16,
  },
  card: {
    borderWidth: 1,
    borderColor: '#e5e7eb',
    borderRadius: 12,
    padding: 16,
    gap: 8,
  },
  error: {
    color: '#dc2626',
  },
  serviceList: {
    gap: 6,
  },
  serviceItem: {
    color: '#374151',
  },
  primaryButton: {
    marginTop: 8,
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
