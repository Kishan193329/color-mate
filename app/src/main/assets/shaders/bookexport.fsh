uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D lut;
uniform highp vec3 outlineColor;
varying highp vec2 texCoord;
void main ()
{
  mediump vec4 r;
  mediump vec4 y;
  mediump vec4 g;
  lowp vec4 x;
  x = texture2D (tex, texCoord);
  g = x;
  lowp vec4 b;
  b = texture2D (outline, texCoord);
  y = b;
  highp vec4 z;
  z.w = 1.0;
  z.xyz = mix (outlineColor, g.xyz, y.y);
  r = z;
  highp vec2 a;
  highp vec2 _;
  mediump vec2 A;
  mediump vec2 Y;
  mediump float R;
  R = (r.z * 63.0);
  Y.y = floor((floor(R) / 8.0));
  Y.x = (floor(R) - (Y.y * 8.0));
  A.y = floor((ceil(R) / 8.0));
  A.x = (ceil(R) - (A.y * 8.0));
  _ = (((Y * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * r.xy));
  a = (((A * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * r.xy));
  lowp vec4 X;
  X = texture2D (lut, _);
  mediump vec4 W;
  W = X;
  lowp vec4 Z;
  Z = texture2D (lut, a);
  mediump vec4 o;
  o = Z;
  mediump float O;
  O = clamp (fract(R), 0.0, 1.0);
  gl_FragColor = ((W * (1.0 - O)) + (o * O));
}

