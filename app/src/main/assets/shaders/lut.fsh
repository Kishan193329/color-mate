uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D lut;
varying highp vec2 texCoord;
void main ()
{
  mediump vec4 r;
  lowp vec4 y;
  y = texture2D (tex, texCoord);
  lowp vec4 g;
  g = texture2D (outline, texCoord);
  lowp vec4 x;
  x.xyz = (y.xyz * g.x);
  x.w = (g.w + y.w);
  r = x;
  highp vec2 b;
  highp vec2 z;
  mediump vec2 a;
  mediump vec2 _;
  mediump float A;
  A = (r.z * 63.0);
  _.y = floor((floor(A) / 8.0));
  _.x = (floor(A) - (_.y * 8.0));
  a.y = floor((ceil(A) / 8.0));
  a.x = (ceil(A) - (a.y * 8.0));
  z = (((_ * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * r.xy));
  b = (((a * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * r.xy));
  lowp vec4 Y;
  Y = texture2D (lut, z);
  mediump vec4 R;
  R = Y;
  lowp vec4 X;
  X = texture2D (lut, b);
  mediump vec4 W;
  W = X;
  mediump float Z;
  Z = clamp (fract(A), 0.0, 1.0);
  gl_FragColor = ((R * (1.0 - Z)) + (W * Z));
}

