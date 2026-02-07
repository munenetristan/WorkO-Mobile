import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';
import { router } from 'expo-router';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useAuth } from '@/hooks/use-auth';
import { apiRequest } from '@/lib/api';

type RegisterResponse = {
  authToken: string;
  role: 'customer' | 'provider';
};

export default function RegisterCustomerScreen() {
  const { state, setAuthToken } = useAuth();
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleRegister = async () => {
    if (!state.otpToken || !state.country) {
      setError('Missing verification token.');
      return;
    }
    if (!firstName || !lastName || !email || !password) {
      setError('Please complete all fields.');
      return;
    }

    setError('');
    setLoading(true);
    try {
      const response = await apiRequest<RegisterResponse>('/api/v1/auth/register', {
        method: 'POST',
        body: JSON.stringify({
          role: 'customer',
          otpToken: state.otpToken,
          firstName,
          lastName,
          email,
          password,
          phone: `${state.country.dialCode}${state.phoneNumber}`,
          countryCode: state.country.iso2,
        }),
      });
      await setAuthToken(response.authToken);
      router.replace('/(customer)');
    } catch (err) {
      setError('Registration failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title" style={styles.title}>
        Create your customer account
      </ThemedText>
      <ThemedText style={styles.subtitle}>Phone number is locked after OTP verification.</ThemedText>

      <View style={styles.inputGroup}>
        <TextInput
          style={styles.input}
          placeholder="First name"
          value={firstName}
          onChangeText={setFirstName}
        />
        <TextInput
          style={styles.input}
          placeholder="Last name"
          value={lastName}
          onChangeText={setLastName}
        />
        <TextInput
          style={styles.input}
          placeholder="Email"
          keyboardType="email-address"
          autoCapitalize="none"
          value={email}
          onChangeText={setEmail}
        />
        <TextInput
          style={styles.input}
          placeholder="Password"
          secureTextEntry
          value={password}
          onChangeText={setPassword}
        />
        <View style={styles.lockedField}>
          <ThemedText>{`${state.country?.dialCode ?? ''}${state.phoneNumber}`}</ThemedText>
        </View>
      </View>
      {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
      <Pressable style={styles.primaryButton} onPress={handleRegister} disabled={loading}>
        <ThemedText style={styles.primaryButtonText}>
          {loading ? 'Creating...' : 'Create Account'}
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
    fontSize: 22,
  },
  subtitle: {
    color: '#6b7280',
  },
  inputGroup: {
    gap: 12,
  },
  input: {
    borderWidth: 1,
    borderColor: '#e5e7eb',
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 12,
  },
  lockedField: {
    borderWidth: 1,
    borderColor: '#e5e7eb',
    borderRadius: 8,
    paddingVertical: 12,
    paddingHorizontal: 12,
    backgroundColor: '#f3f4f6',
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
