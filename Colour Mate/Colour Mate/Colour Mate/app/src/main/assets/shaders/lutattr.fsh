uniform sampler2D tex;
varying highp vec2 texCoord;
uniform highp float lutPower;
uniform highp vec2 brightnessContrast;
uniform highp vec2 saturationVibrance;
uniform highp float temperature;
void main ()
{
  highp vec3 r;
  mediump vec3 y;
  highp float g;
  g = (texCoord.x * 7.99999);
  highp float x;
  x = (texCoord.y * 7.99999);
  highp vec3 b;
  b.x = fract(g);
  b.y = fract(x);
  b.z = (((
    floor(x)
   * 8.0) + floor(g)) / 63.99999);
  y = b;
  lowp vec4 z;
  z = texture2D (tex, texCoord);
  highp vec3 a;
  a = mix (y, z.xyz, lutPower);
  highp float _;
  _ = dot (a, vec3(0.299, 0.587, 0.114));
  highp vec3 A;
  A.y = 1.0;
  A.x = (1.0 + temperature);
  A.z = (1.0 - temperature);
  r = (a * A);
  highp vec3 Y;
  Y = mix (vec3(_), r, saturationVibrance.x);
  highp vec3 R;
  R = (Y - vec3(_));
  r = (pow (mix (Y, vec3(_), 
    (saturationVibrance.y / (sqrt(dot (R, R)) + 0.1))
  ), vec3((1.0/(
    (1.0 + brightnessContrast.x)
  )))) * vec3((1.0 + (brightnessContrast.x * 0.5))));
  r = (((r - vec3(0.5, 0.5, 0.5)) * brightnessContrast.y) + vec3(0.5, 0.5, 0.5));
  highp vec4 X;
  X.w = 1.0;
  X.xyz = r;
  gl_FragColor = X;
}

