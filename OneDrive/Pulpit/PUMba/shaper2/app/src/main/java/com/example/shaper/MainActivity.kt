package com.example.shaper

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

// ==========================================
// 1. GŁÓWNA AKTYWNOŚĆ
// ==========================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitnessApp()
        }
    }
}

// ==========================================
// 2. BAZA DANYCH (Room)
// ==========================================

// Tabele (Entities)
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1, // Jedno konto w aplikacji
    val startWeight: Float,
    val targetWeight: Float,
    val rateKgPerWeek: Float,
    val age: Int,
    val gender: String, // "M" lub "K"
    val activityLevel: Float, // Mnożnik aktywności
    val startDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "weight_entries")
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val weight: Float
)

// Zapytania SQL (DAO)
@Dao
interface FitnessDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getProfile(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightEntry(entry: WeightEntryEntity)

    @Query("SELECT * FROM weight_entries ORDER BY date ASC")
    fun getAllWeightEntries(): Flow<List<WeightEntryEntity>>
}

// Konfiguracja Bazy
@Database(entities = [UserProfileEntity::class, WeightEntryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fitnessDao(): FitnessDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fitness_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ==========================================
// 3. LOGIKA BIZNESOWA (ViewModel)
// ==========================================

class FitnessViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).fitnessDao()

    // Automatycznie odświeżające się strumienie danych z bazy
    val userProfile: StateFlow<UserProfileEntity?> = dao.getProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val weightEntries: StateFlow<List<WeightEntryEntity>> = dao.getAllWeightEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveProfile(
        currentWeight: Float, targetWeight: Float, rateKgPerWeek: Float,
        age: Int, gender: String, activityLevel: Float
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = UserProfileEntity(
                startWeight = currentWeight,
                targetWeight = targetWeight,
                rateKgPerWeek = rateKgPerWeek,
                age = age,
                gender = gender,
                activityLevel = activityLevel
            )
            dao.insertProfile(profile)
            // Dodajemy pierwszą wagę do historii
            dao.insertWeightEntry(WeightEntryEntity(date = System.currentTimeMillis(), weight = currentWeight))
        }
    }

    fun addWeightEntry(weight: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertWeightEntry(WeightEntryEntity(date = System.currentTimeMillis(), weight = weight))
        }
    }

    // Zaawansowane obliczanie kalorii (wzór Mifflin-St Jeor)
    fun calculateDailyCalories(): Int {
        val profile = userProfile.value ?: return 0
        val lastWeight = weightEntries.value.lastOrNull()?.weight ?: profile.startWeight

        val genderOffset = if (profile.gender == "M") 5 else -161
        val bmr = (10 * lastWeight) + (6.25f * 175) - (5 * profile.age) + genderOffset
        val tdee = bmr * profile.activityLevel
        val dailyCaloricDifference = ((profile.rateKgPerWeek * 7700) / 7).toInt()

        return if (profile.targetWeight < profile.startWeight) {
            (tdee - dailyCaloricDifference).toInt() // Redukcja
        } else {
            (tdee + dailyCaloricDifference).toInt() // Masa
        }
    }

    fun calculateRemainingDays(): Int {
        val profile = userProfile.value ?: return 0
        val lastWeight = weightEntries.value.lastOrNull()?.weight ?: profile.startWeight
        val weightDiff = abs(profile.targetWeight - lastWeight)

        if (profile.rateKgPerWeek <= 0f) return 0
        return ((weightDiff / profile.rateKgPerWeek) * 7).toInt()
    }
}

// ==========================================
// 4. INTERFEJS UŻYTKOWNIKA (Compose)
// ==========================================

@Composable
fun FitnessApp() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val navController = rememberNavController()
            val viewModel: FitnessViewModel = viewModel()
            val profile by viewModel.userProfile.collectAsState()

            Scaffold(
                bottomBar = {
                    if (profile != null) {
                        BottomNavigationBar(navController)
                    }
                }
            ) { paddingValues ->
                NavHost(
                    navController = navController,
                    startDestination = "register",
                    modifier = Modifier.padding(paddingValues)
                ) {
                    composable("register") { RegistrationScreen(navController, viewModel) }
                    composable("profile_setup") { ProfileSetupScreen(navController, viewModel) }
                    composable("dashboard") { DashboardScreen(viewModel) }
                    composable("add_weight") { AddWeightScreen(navController, viewModel) }
                    composable("chart") { ChartScreen(viewModel) }
                }
            }
        }
    }
}

@Composable
fun RegistrationScreen(navController: NavHostController, viewModel: FitnessViewModel) {
    val profile by viewModel.userProfile.collectAsState()

    // Automatyczne logowanie, jeśli profil istnieje
    LaunchedEffect(profile) {
        if (profile != null) {
            navController.navigate("dashboard") {
                popUpTo("register") { inclusive = true }
            }
        }
    }

    if (profile == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Witaj w FitApp", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { navController.navigate("profile_setup") },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Stwórz nowe konto")
            }
        }
    }
}

@Composable
fun ProfileSetupScreen(navController: NavHostController, viewModel: FitnessViewModel) {
    var currentWeight by remember { mutableStateOf("") }
    var targetWeight by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("0.5") }
    var age by remember { mutableStateOf("") }

    var selectedGender by remember { mutableStateOf("M") }
    var selectedActivity by remember { mutableStateOf(1.2f) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Stwórz swój profil", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        // Wybór płci
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(
                onClick = { selectedGender = "M" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedGender == "M") MaterialTheme.colorScheme.primary else Color.Gray
                )
            ) { Text("Mężczyzna") }

            Button(
                onClick = { selectedGender = "K" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedGender == "K") MaterialTheme.colorScheme.primary else Color.Gray
                )
            ) { Text("Kobieta") }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = age,
                onValueChange = { age = it },
                label = { Text("Wiek") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = currentWeight,
                onValueChange = { currentWeight = it },
                label = { Text("Waga (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = targetWeight,
            onValueChange = { targetWeight = it },
            label = { Text("Cel wagi (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = rate,
            onValueChange = { rate = it },
            label = { Text("Tempo (kg / tydzień)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("Poziom aktywności", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(
                onClick = { selectedActivity = 1.2f },
                colors = ButtonDefaults.buttonColors(containerColor = if (selectedActivity == 1.2f) MaterialTheme.colorScheme.primary else Color.Gray)
            ) { Text("Mała") }
            Button(
                onClick = { selectedActivity = 1.55f },
                colors = ButtonDefaults.buttonColors(containerColor = if (selectedActivity == 1.55f) MaterialTheme.colorScheme.primary else Color.Gray)
            ) { Text("Średnia") }
            Button(
                onClick = { selectedActivity = 1.75f },
                colors = ButtonDefaults.buttonColors(containerColor = if (selectedActivity == 1.75f) MaterialTheme.colorScheme.primary else Color.Gray)
            ) { Text("Duża") }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val cw = currentWeight.toFloatOrNull()
                val tw = targetWeight.toFloatOrNull()
                val r = rate.toFloatOrNull()
                val a = age.toIntOrNull()

                if (cw != null && tw != null && r != null && a != null) {
                    viewModel.saveProfile(cw, tw, r, a, selectedGender, selectedActivity)
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Zapisz profil i ruszamy!")
        }
    }
}

@Composable
fun DashboardScreen(viewModel: FitnessViewModel) {
    val calories = viewModel.calculateDailyCalories()
    val daysLeft = viewModel.calculateRemainingDays()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Twój cel na dziś", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("$calories kcal", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Pozostało do celu", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("$daysLeft dni", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AddWeightScreen(navController: NavHostController, viewModel: FitnessViewModel) {
    var newWeight by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Dodaj dzisiejszą wagę", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = newWeight,
            onValueChange = { newWeight = it },
            label = { Text("Waga (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                newWeight.toFloatOrNull()?.let {
                    viewModel.addWeightEntry(it)
                    navController.navigate("dashboard") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Zapisz")
        }
    }
}

@Composable
fun ChartScreen(viewModel: FitnessViewModel) {
    val entries by viewModel.weightEntries.collectAsState()
    val profile by viewModel.userProfile.collectAsState()

    if (profile == null || entries.isEmpty()) return

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Postęp wagi", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LegendItem(color = Color.Gray, text = "Książkowa")
            LegendItem(color = Color.Cyan, text = "Twoja")
            LegendItem(color = Color.Cyan.copy(alpha = 0.5f), text = "Przewidywana", isDashed = true)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Canvas(modifier = Modifier.fillMaxWidth().weight(1f).background(Color.DarkGray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))) {
            val width = size.width
            val height = size.height

            val minWeight = minOf(profile!!.startWeight, profile!!.targetWeight, entries.minOf { it.weight }) - 2f
            val maxWeight = maxOf(profile!!.startWeight, profile!!.targetWeight, entries.maxOf { it.weight }) + 2f
            val weightRange = maxWeight - minWeight

            val startTime = profile!!.startDate
            val totalPredictedDays = (abs(profile!!.startWeight - profile!!.targetWeight) / (profile!!.rateKgPerWeek / 7f)).toLong()
            val totalTimeRange = TimeUnit.DAYS.toMillis(totalPredictedDays)
            val endTime = startTime + totalTimeRange

            fun mapToX(time: Long): Float = ((time - startTime).toFloat() / totalTimeRange.toFloat()) * width
            fun mapToY(weight: Float): Float = height - (((weight - minWeight) / weightRange) * height)

            drawLine(
                color = Color.Gray,
                start = Offset(mapToX(startTime), mapToY(profile!!.startWeight)),
                end = Offset(mapToX(endTime), mapToY(profile!!.targetWeight)),
                strokeWidth = 4f
            )

            if (entries.size > 1) {
                for (i in 0 until entries.size - 1) {
                    drawLine(
                        color = Color.Cyan,
                        start = Offset(mapToX(entries[i].date), mapToY(entries[i].weight)),
                        end = Offset(mapToX(entries[i + 1].date), mapToY(entries[i + 1].weight)),
                        strokeWidth = 6f
                    )
                }
            } else if (entries.size == 1) {
                drawCircle(color = Color.Cyan, radius = 8f, center = Offset(mapToX(entries[0].date), mapToY(entries[0].weight)))
            }

            if (entries.isNotEmpty()) {
                val lastEntry = entries.last()
                val ratePerDay = profile!!.rateKgPerWeek / 7f
                val daysNeeded = abs(lastEntry.weight - profile!!.targetWeight) / ratePerDay
                val predictedEndTime = lastEntry.date + TimeUnit.DAYS.toMillis(daysNeeded.toLong())

                drawLine(
                    color = Color.Cyan.copy(alpha = 0.5f),
                    start = Offset(mapToX(lastEntry.date), mapToY(lastEntry.weight)),
                    end = Offset(mapToX(predictedEndTime), mapToY(profile!!.targetWeight)),
                    strokeWidth = 4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                )
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, text: String, isDashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(20.dp, 4.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 4f,
                pathEffect = if (isDashed) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(
        Triple("dashboard", "Główny", Icons.Filled.Home),
        Triple("add_weight", "Dodaj wagę", Icons.Filled.Add),
        Triple("chart", "Wykres", Icons.Filled.Info)
    )

    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        items.forEach { (route, label, icon) ->
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                selected = currentRoute == route,
                onClick = {
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            popUpTo("dashboard") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}