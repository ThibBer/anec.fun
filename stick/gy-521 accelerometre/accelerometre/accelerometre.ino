#include <Wire.h>
#include <float.h>
#include <MPU6050.h>

#define ACCEL_TRESHOLD 3 // meter / second
#define ACCEL_SIZE 3 // number of accelerations saved to detect movement

double previousNormalizedAcceleration = DBL_MAX;
double normalizedAccelerations[ACCEL_SIZE];
int index = 0;

MPU6050 mpu;

void setup(){
  Wire.begin();
  Serial.begin(115200);
  
  while(!mpu.begin(MPU6050_SCALE_2000DPS, MPU6050_RANGE_2G))
  {
    Serial.println("Could not find a valid MPU6050 sensor, check wiring!");
  }
  
  mpu.calibrateGyro();
}

void loop(){
  Vector rawAccel = mpu.readNormalizeAccel();
  double normalizedAcceleration = sqrt(pow(rawAccel.XAxis, 2) + pow(rawAccel.YAxis, 2) + pow(rawAccel.ZAxis, 2));
  normalizedAccelerations[index] = normalizedAcceleration;

  Serial.print("Prev_acc:");
  Serial.print(previousNormalizedAcceleration);
  Serial.print(", Norm_acc:");
  Serial.println(normalizedAcceleration);

  float totalDeltaNormalized = 0;

  for (int i = 1; i < ACCEL_SIZE; i++) {
    totalDeltaNormalized += abs(normalizedAccelerations[i] - normalizedAccelerations[i - 1]);
  }

  if(totalDeltaNormalized > ACCEL_TRESHOLD){
    Serial.println("Gros mouvement détecté");
  }

  previousNormalizedAcceleration = normalizedAcceleration;
  index = (index + 1) % ACCEL_SIZE;

  delay(300);
}
