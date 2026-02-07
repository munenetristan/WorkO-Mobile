import { useState } from 'react';
import { Pressable, StyleSheet, TextInput } from 'react-native';
import { router } from 'expo-router';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useAuth } from '@/hooks/use-auth';
import { apiRequest } from '@/lib/api';

type VerifyResponse = {
  otpToken: string;
  needsRegistration: boolean;
  role: 'customer' | 'provider';
  authToken?: string;
};

export default function OtpScreen() {
  const { state, setOtpToken, setRole, setAuthToken } = useAuth();
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleVerify = async () => {
    if (!state.country) {
      setError('Missing country selection.');
      return;
    }
    if (!code) {
      setError('Enter the OTP code.');
      return;
    }
    setError('');
    setLoading(true);
    try {
      const response = await apiRequest<VerifyResponse>('/api/v1/auth/otp/verify', {
        method: 'POST',
        body: JSON.stringify({
          phone: `${state.country.dialCode}${state.phoneNumber}`,
          countryCode: state.country.iso2,
          otp: code,
        }),
      });
      setOtpToken(response.otpToken);
      setRole(response.role);

      if (response.needsRegistration) {
        router.replace(
          response.role === 'provider' ? '/(auth)/register-provider' : '/(auth)/register-customer'
        );
        return;
      }

      if (response.authToken) {
        await setAuthToken(response.authToken);
        router.replace(response.role === 'provider' ? '/(provider)' : '/(customer)');
      }
    } catch (err) {
      setError('OTP verification failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title" style={styles.title}>
        Enter OTP
      </ThemedText>
      <ThemedText style={styles.subtitle}>
        Enter the 4-6 digit code sent to your phone.
      </ThemedText>
      <TextInput
        style={styles.input}
        keyboardType="number-pad"
        placeholder="OTP code"
        value={code}
        onChangeText={setCode}
      />
      {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
      <Pressable style={styles.primaryButton} onPress={handleVerify} disabled={loading}>
        <ThemedText style={styles.primaryButtonText}>
          {loading ? 'Verifying...' : 'Verify OTP'}
        </ThemedText>
      </Pressable>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    gap: 16,
  },
  title: {
    fontSize: 24,
  },
  subtitle: {
    color: '#6b7280',
  },
  input: {
    borderWidth: 1,
    borderColor: '#e5e7eb',
    borderRadius: 8,
    paddingVertical: 14,
    paddingHorizontal: 12,
  },
  error: {
    color: '#dc2626',
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
