import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
import { router } from 'expo-router';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useAuth } from '@/hooks/use-auth';
import { apiRequest } from '@/lib/api';

export default function PhoneScreen() {
  const { state, setPhoneNumber } = useAuth();
  const [phone, setPhone] = useState(state.phoneNumber);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const selectedCountry = state.country;

  const handleRequestOtp = async () => {
    if (!selectedCountry) {
      setError('Please select a country.');
      return;
    }
    if (!phone) {
      setError('Enter your phone number.');
      return;
    }
    setError('');
    setLoading(true);
    try {
      await apiRequest('/api/v1/auth/otp/request', {
        method: 'POST',
        body: JSON.stringify({
          phone: `${selectedCountry.dialCode}${phone}`,
          countryCode: selectedCountry.iso2,
        }),
      });
      setPhoneNumber(phone);
      router.push('/(auth)/otp');
    } catch (err) {
      setError('Unable to request OTP. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title" style={styles.title}>
        Enter your phone number
      </ThemedText>
      <ThemedText style={styles.subtitle}>
        We will send a one-time passcode to verify your account.
      </ThemedText>
      <View style={styles.inputRow}>
        <View style={styles.dialCodeBox}>
          <ThemedText type="defaultSemiBold">
            {selectedCountry?.dialCode ?? '+--'}
          </ThemedText>
        </View>
        <TextInput
          style={styles.input}
          keyboardType="phone-pad"
          placeholder="Phone number"
          value={phone}
          onChangeText={setPhone}
        />
      </View>
      {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
      <Pressable style={styles.primaryButton} onPress={handleRequestOtp} disabled={loading}>
        <ThemedText style={styles.primaryButtonText}>
          {loading ? 'Requesting...' : 'Request OTP'}
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
  inputRow: {
    flexDirection: 'row',
    gap: 12,
    alignItems: 'center',
  },
  dialCodeBox: {
    paddingVertical: 14,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderColor: '#e5e7eb',
    borderRadius: 8,
  },
  input: {
    flex: 1,
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
