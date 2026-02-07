// app/src/main/java/com/towmech/app/navigation/NavGraph.kt
package com.towmech.app.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.towmech.app.BuildConfig
import com.towmech.app.api.ApiClient
import com.towmech.app.api.CheckPhoneExistsRequest
import com.towmech.app.data.TokenManager
import com.towmech.app.screens.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.launch

@Composable
fun AppNavGraph(
    startDestination: String = Routes.COUNTRY_START,
    notificationOpen: String? = null,
    notificationJobId: String? = null
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun isCustomerApp(): Boolean = BuildConfig.APP_FLAVOR == "customer"
    fun isProviderApp(): Boolean = BuildConfig.APP_FLAVOR == "provider"

    LaunchedEffect(Unit) {
        val token = TokenManager.getToken(context)
        if (token.isNullOrBlank()) return@LaunchedEffect

        val roleFromApi: String? = try {
            val profile = ApiClient.apiService.getProfile("Bearer $token")
            profile.user?.role
        } catch (_: Exception) {
            null
        }

        val isCustomerRole = roleFromApi?.equals("Customer", ignoreCase = true) == true
        val isProviderRole = !isCustomerRole

        val target = when {
            isCustomerApp() && isCustomerRole -> Routes.CUSTOMER_MAIN
            isProviderApp() && isProviderRole -> Routes.PROVIDER_MAIN
            else -> {
                TokenManager.clearToken(context)
                Routes.LOGIN
            }
        }

        navController.navigate(target) {
            popUpTo(Routes.COUNTRY_START) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.COUNTRY_START) {
            CountryStartScreen(
                onOtpRequested = { fullPhone ->
                    val safePhone = URLEncoder.encode(fullPhone, StandardCharsets.UTF_8.toString())
                    navController.navigate("${Routes.COUNTRY_VERIFY_OTP}/$safePhone")
                }
            )
        }

        composable(
            route = "${Routes.COUNTRY_VERIFY_OTP}/{phone}",
            arguments = listOf(navArgument("phone") { type = NavType.StringType })
        ) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""

            CountryVerifyOtpScreen(
                phone = phone,
                onVerified = { verifiedPhone ->
                    scope.launch {
                        val countryCode =
                            TokenManager.getCountryCode(context)?.trim()?.uppercase().orEmpty()

                        val exists: Boolean = try {
                            val res = ApiClient.apiService.checkPhoneExists(
                                CheckPhoneExistsRequest(
                                    phone = verifiedPhone.trim(),
                                    countryCode = countryCode
                                )
                            )
                            res.exists
                        } catch (_: Exception) {
                            false
                        }

                        val target = when {
                            exists -> Routes.LOGIN
                            isCustomerApp() -> Routes.REGISTER_CUSTOMER
                            else -> Routes.ROLE_SELECT
                        }

                        navController.navigate(target) {
                            popUpTo(Routes.COUNTRY_START) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.START) {
            StartScreen(
                onLogin = { navController.navigate(Routes.LOGIN) },
                onSignup = {
                    if (BuildConfig.APP_FLAVOR == "customer") {
                        navController.navigate(Routes.REGISTER_CUSTOMER)
                    } else {
                        navController.navigate(Routes.ROLE_SELECT)
                    }
                }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { phone ->
                    val safePhone = URLEncoder.encode(phone, StandardCharsets.UTF_8.toString())
                    navController.navigate("${Routes.VERIFY_OTP}/$safePhone")
                },
                onForgotPassword = { phone ->
                    val safePhone = URLEncoder.encode(phone, StandardCharsets.UTF_8.toString())
                    navController.navigate("${Routes.FORGOT_PASSWORD}/$safePhone")
                }
            )
        }

        composable(
            route = "${Routes.FORGOT_PASSWORD}/{phone}",
            arguments = listOf(navArgument("phone") { type = NavType.StringType })
        ) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            ForgotPasswordScreen(
                initialPhone = phone,
                onOtpSent = { sentPhone ->
                    val safePhone = URLEncoder.encode(sentPhone, StandardCharsets.UTF_8.toString())
                    navController.navigate("${Routes.RESET_PASSWORD}/$safePhone")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "${Routes.RESET_PASSWORD}/{phone}",
            arguments = listOf(navArgument("phone") { type = NavType.StringType })
        ) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            ResetPasswordScreen(
                phone = phone,
                onResetSuccess = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ROLE_SELECT) {
            if (BuildConfig.APP_FLAVOR == "customer") {
                LaunchedEffect(Unit) { navController.navigate(Routes.REGISTER_CUSTOMER) }
            } else {
                RoleSelectScreen(
                    onTowTruck = { navController.navigate(Routes.REGISTER_TOWTRUCK) },
                    onMechanic = { navController.navigate(Routes.REGISTER_MECHANIC) },
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Routes.REGISTER_CUSTOMER) {
            RegisterCustomerScreen(
                onBack = { navController.popBackStack() },
                onOtpRequested = { phone ->
                    val safePhone = URLEncoder.encode(phone, StandardCharsets.UTF_8.toString())
                    navController.navigate("${Routes.VERIFY_OTP}/$safePhone")
                }
            )
        }

        composable(Routes.REGISTER_TOWTRUCK) {
            if (BuildConfig.APP_FLAVOR != "provider") {
                LaunchedEffect(Unit) { navController.navigate(Routes.START) }
            } else {
                RegisterTowTruckScreen(
                    onBack = { navController.popBackStack() },
                    onOtpRequested = { phone ->
                        val safePhone = URLEncoder.encode(phone, StandardCharsets.UTF_8.toString())
                        navController.navigate("${Routes.VERIFY_OTP}/$safePhone")
                    }
                )
            }
        }

        composable(Routes.REGISTER_MECHANIC) {
            if (BuildConfig.APP_FLAVOR != "provider") {
                LaunchedEffect(Unit) { navController.navigate(Routes.START) }
            } else {
                RegisterMechanicScreen(
                    onBack = { navController.popBackStack() },
                    onOtpRequested = { phone ->
                        val safePhone = URLEncoder.encode(phone, StandardCharsets.UTF_8.toString())
                        navController.navigate("${Routes.VERIFY_OTP}/$safePhone")
                    }
                )
            }
        }

        composable(
            route = "${Routes.VERIFY_OTP}/{phone}",
            arguments = listOf(navArgument("phone") { type = NavType.StringType })
        ) { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""

            VerifyOtpScreen(
                phone = phone,
                onVerified = { role ->
                    val isCustomerRole = role.equals("Customer", ignoreCase = true)
                    val target = when {
                        isCustomerApp() && isCustomerRole -> Routes.CUSTOMER_MAIN
                        isProviderApp() && !isCustomerRole -> Routes.PROVIDER_MAIN
                        else -> {
                            TokenManager.clearToken(context)
                            Routes.LOGIN
                        }
                    }

                    navController.navigate(target) {
                        popUpTo(Routes.COUNTRY_START) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CUSTOMER_MAIN) {
            if (BuildConfig.APP_FLAVOR != "customer") {
                LaunchedEffect(Unit) { navController.navigate(Routes.LOGIN) }
            } else {
                CustomerMainScreen(
                    onGoRequestService = { navController.navigate(Routes.REQUEST_SERVICE) },
                    onOpenTracking = { jobId ->
                        navController.navigate("${Routes.CUSTOMER_JOB_TRACKING}/$jobId")
                    },
                    onLogout = {
                        TokenManager.clearToken(context)
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.CUSTOMER_MAIN) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        composable(Routes.PROVIDER_MAIN) {
            if (BuildConfig.APP_FLAVOR != "provider") {
                LaunchedEffect(Unit) { navController.navigate(Routes.LOGIN) }
            } else {
                ProviderMainScreen(
                    notificationOpen = notificationOpen,
                    notificationJobId = notificationJobId,
                    onLoggedOut = {
                        TokenManager.clearToken(context)
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.PROVIDER_MAIN) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        composable(Routes.HOME_PROVIDER) {
            if (BuildConfig.APP_FLAVOR != "provider") {
                LaunchedEffect(Unit) { navController.navigate(Routes.LOGIN) }
            } else {
                HomeProviderScreen(
                    onOpenTracking = { jobId ->
                        navController.navigate("provider_job_tracking/$jobId")
                    }
                )
            }
        }

        composable(
            route = "provider_job_tracking/{jobId}",
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            if (BuildConfig.APP_FLAVOR != "provider") {
                LaunchedEffect(Unit) { navController.navigate(Routes.LOGIN) }
            } else {
                val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
                ProviderJobTrackingScreen(
                    jobId = jobId,
                    onBack = { navController.popBackStack() },
                    onJobCompleted = {
                        navController.navigate(Routes.PROVIDER_MAIN) {
                            popUpTo(Routes.PROVIDER_MAIN) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        // ✅ Customer job flow (customer app only)
        composable(Routes.REQUEST_SERVICE) {
            if (BuildConfig.APP_FLAVOR != "customer") {
                LaunchedEffect(Unit) { navController.navigate(Routes.LOGIN) }
            } else {

                // ✅ Keep old debug log (optional)
                LaunchedEffect(Unit) {
                    try {
                        val cfg = ApiClient.apiService.getAppConfig()
                        Log.d("CONFIG_ALL_RAW", cfg.toString())
                    } catch (_: Exception) {}
                }

                RequestServiceScreen(
                    onBack = { navController.popBackStack() },

                    // old callbacks kept (not used here)
                    onTowTruckSelected = {},
                    onMechanicSelected = {},

                    // ✅ UPDATED: insurance-aware callbacks now include partnerId
                    onTowTruckSelectedWithInsurance = { insuranceEnabled, insuranceCode, insurancePartnerId ->
                        navController.currentBackStackEntry?.savedStateHandle?.set("insuranceEnabled", insuranceEnabled)
                        navController.currentBackStackEntry?.savedStateHandle?.set("insuranceCode", insuranceCode)
                        navController.currentBackStackEntry?.savedStateHandle?.set("insurancePartnerId", insurancePartnerId)
                        navController.navigate("${Routes.JOB_PREVIEW}/TowTruck")
                    },
                    onMechanicSelectedWithInsurance = { insuranceEnabled, insuranceCode, insurancePartnerId ->
                        navController.currentBackStackEntry?.savedStateHandle?.set("insuranceEnabled", insuranceEnabled)
                        navController.currentBackStackEntry?.savedStateHandle?.set("insuranceCode", insuranceCode)
                        navController.currentBackStackEntry?.savedStateHandle?.set("insurancePartnerId", insurancePartnerId)
                        navController.navigate("${Routes.JOB_PREVIEW}/Mechanic")
                    }
                )
            }
        }

        composable(
            route = "${Routes.JOB_PREVIEW}/{roleNeeded}",
            arguments = listOf(navArgument("roleNeeded") { type = NavType.StringType })
        ) { backStackEntry ->
            if (BuildConfig.APP_FLAVOR != "customer") {
                LaunchedEffect(Unit) { navController.navigate(Routes.LOGIN) }
            } else {
                val roleNeeded = backStackEntry.arguments?.getString("roleNeeded") ?: "Mechanic"
                JobPreviewScreen(
                    navController = navController,
                    roleNeeded = roleNeeded,
                    onBack = { navController.popBackStack() },
                    onProceedToPayment = { _, _, _ ->
                        navController.navigate("${Routes.JOB_PREVIEW_DETAILS}/$roleNeeded")
                    }
                )
            }
        }

        composable(
            route = "${Routes.JOB_PREVIEW_DETAILS}/{roleNeeded}",
            arguments = listOf(navArgument("roleNeeded") { type = NavType.StringType })
        ) { backStackEntry ->
            if (BuildConfig.APP_FLAVOR != "customer") {
                LaunchedEffect(Unit) { navController.navigate(Routes.LOGIN) }
            } else {
                val roleNeeded = backStackEntry.arguments?.getString("roleNeeded") ?: "TowTruck"
                JobPreviewDetailsScreen(
                    navController = navController,
                    roleNeeded = roleNeeded,
                    onBack = { navController.popBackStack() },
                    onProceedToPayment = { jobId, bookingFee, currency ->
                        if (bookingFee <= 0) {
                            navController.navigate("${Routes.JOB_SEARCHING}/$jobId")
                        } else {
                            navController.navigate("${Routes.BOOKING_PAYMENT}/$jobId/$bookingFee/$currency")
                        }
                    }
                )
            }
        }

        composable(
            route = "${Routes.PICK_LOCATION}/{type}",
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) { backStackEntry ->
            if (BuildConfig.APP_FLAVOR != "customer") {
                LaunchedEffect(Unit) { navController.navigate(Routes.LOGIN) }
            } else {
                val type = backStackEntry.arguments?.getString("type") ?: "pickup"
                PickLocationScreen(
                    type = type,
                    onBack = { navController.popBackStack() },
                    onLocationPicked = { lat: Double, lng: Double, address: String ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("picked_lat", lat)
                        navController.previousBackStackEntry?.savedStateHandle?.set("picked_lng", lng)
                        navController.previousBackStackEntry?.savedStateHandle?.set("picked_address", address)
                        navController.previousBackStackEntry?.savedStateHandle?.set("picked_type", type)
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(
            route = "${Routes.BOOKING_PAYMENT}/{jobId}/{bookingFee}/{currency}",
            arguments = listOf(
                navArgument("jobId") { type = NavType.StringType },
                navArgument("bookingFee") { type = NavType.IntType },
                navArgument("currency") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            if (BuildConfig.APP_FLAVOR != "customer") {
                LaunchedEffect(Unit) { navController.navigate(Routes.LOGIN) }
            } else {
                val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
                val bookingFee = backStackEntry.arguments?.getInt("bookingFee") ?: 0
                val currency = backStackEntry.arguments?.getString("currency") ?: "ZAR"

                BookingFeePaymentScreen(
                    jobId = jobId,
                    bookingFee = bookingFee,
                    currency = currency,
                    onBack = { navController.popBackStack() },
                    onPaymentConfirmed = {
                        navController.navigate("${Routes.JOB_SEARCHING}/$jobId")
                    }
                )
            }
        }

        composable(
            route = "${Routes.JOB_SEARCHING}/{jobId}",
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            if (BuildConfig.APP_FLAVOR != "customer") {
                LaunchedEffect(Unit) { navController.navigate(Routes.LOGIN) }
            } else {
                val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
                JobSearchingScreen(
                    jobId = jobId,
                    onProviderAssigned = { assignedJobId ->
                        navController.navigate("${Routes.CUSTOMER_JOB_TRACKING}/$assignedJobId") {
                            popUpTo("${Routes.JOB_SEARCHING}/$jobId") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        composable(
            route = "${Routes.CUSTOMER_JOB_TRACKING}/{jobId}",
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            if (BuildConfig.APP_FLAVOR != "customer") {
                LaunchedEffect(Unit) { navController.navigate(Routes.LOGIN) }
            } else {
                val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
                CustomerJobTrackingScreen(
                    jobId = jobId,
                    onBack = { navController.popBackStack() },
                    onJobCompleted = {
                        navController.navigate(Routes.CUSTOMER_MAIN) {
                            popUpTo(Routes.CUSTOMER_MAIN) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        composable(
            route = "${Routes.RATE_SERVICE}/{jobId}",
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            if (BuildConfig.APP_FLAVOR != "customer") {
                LaunchedEffect(Unit) { navController.navigate(Routes.LOGIN) }
            } else {
                val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
                RateServiceScreen(
                    jobId = jobId,
                    onBack = { navController.popBackStack() },
                    onSubmitRating = {
                        navController.navigate(Routes.CUSTOMER_MAIN) {
                            popUpTo(Routes.RATE_SERVICE) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}