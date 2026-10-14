uniform sampler2D mainInput;
uniform sampler2D lut;
varying highp vec2 texCoord;
void main ()
{
  lowp vec4 r;
  r = texture2D (mainInput, texCoord);
  mediump vec4 y;
  y = r;
  highp vec2 g;
  highp vec2 x;
  mediump vec2 b;
  mediump vec2 z;
  mediump float a;
  a = (y.z * 63.0);
  z.y = floor((floor(a) / 8.0));
  z.x = (floor(a) - (z.y * 8.0));
  b.y = floor((ceil(a) / 8.0));
  b.x = (ceil(a) - (b.y * 8.0));
  x = (((z * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * y.xy));
  g = (((b * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * y.xy));
  lowp vec4 _;
  _ = texture2D (lut, x);
  mediump vec4 A;
  A = _;
  lowp vec4 Y;
  Y = texture2D (lut, g);
  mediump vec4 R;
  R = Y;
  mediump float X;
  X = clamp (fract(a), 0.0, 1.0);
  gl_FragColor = ((A * (1.0 - X)) + (R * X));
}

