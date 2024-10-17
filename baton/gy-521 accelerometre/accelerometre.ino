#include <Wire.h>
#include <MPU6050.h>

int ax, ay, az;
int gx, gy, gz;

MPU6050 mpu;

void setup(){
  Wire.begin();
  Serial.begin(115200);
  
  while(!mpu.begin(MPU6050_SCALE_2000DPS, MPU6050_RANGE_2G))
  {
    Serial.println("Could not find a valid MPU6050 sensor, check wiring!");
  }
  
  mpu.calibrateGyro();
  //mpu.setMotionDetectionThreshold(2);
  //mpu.setMotionDetectionDuration(5);

  //mpu.setZeroMotionDetectionThreshold(2);
  //mpu.setZeroMotionDetectionDuration(2);	
}

void loop(){
  Vector rawAccel = mpu.readNormalizeAccel();
  Activites act = mpu.readActivites();
  double sum = rawAccel.XAxis + rawAccel.YAxis + rawAccel.ZAxis;

  Serial.print("Accel (");
  Serial.print(rawAccel.XAxis);
  Serial.print(", ");
  Serial.print(rawAccel.YAxis);
  Serial.print(", ");
  Serial.print(rawAccel.ZAxis);
  Serial.print(", sum : ");
  Serial.print(sum);
  Serial.print(") activity : ");

  Serial.print(act.isActivity);

  Serial.print(" | x : ");
  Serial.print(act.isPosActivityOnX || act.isNegActivityOnX);

  Serial.print(" y : ");
  Serial.print(act.isPosActivityOnY || act.isNegActivityOnY);

  Serial.print(" z : ");
  Serial.print(act.isPosActivityOnZ || act.isNegActivityOnZ);


  bool isMoving = sum > 12 || sum < -5;
  Serial.print(" - is moving : ");
  Serial.println(isMoving);

  delay(100);
}
