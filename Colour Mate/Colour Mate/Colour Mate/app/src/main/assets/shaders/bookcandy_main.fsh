uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D normalMap;
uniform sampler2D specularMap;
uniform sampler2D lut;
varying highp vec2 texCoord;
uniform mediump vec3 outlineColor;
void main ()
{
  mediump vec3 r;
  mediump vec3 y;
  highp vec3 g;
  mediump vec4 x;
  lowp vec4 b;
  b = texture2D (outline, texCoord);
  x = b;
  lowp vec3 z;
  z = normalize((texture2D (normalMap, texCoord).xyz - vec3(0.5, 0.5, 0.5)));
  g = z;
  highp vec2 a;
  a = ((g.xy * 0.5) + vec2(0.5, 0.5));
  lowp vec3 _;
  _ = texture2D (specularMap, a).xyz;
  y = _;
  lowp vec3 A;
  A = texture2D (tex, texCoord).xyz;
  r = A;
  mediump vec4 Y;
  Y.w = 1.0;
  Y.xyz = mix (outlineColor, r, x.y);
  highp vec2 R;
  highp vec2 X;
  mediump vec2 W;
  mediump vec2 Z;
  mediump float o;
  o = (Y.z * 63.0);
  Z.y = floor((floor(o) / 8.0));
  Z.x = (floor(o) - (Z.y * 8.0));
  W.y = floor((ceil(o) / 8.0));
  W.x = (ceil(o) - (W.y * 8.0));
  X = (((Z * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * Y.xy));
  R = (((W * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * Y.xy));
  lowp vec4 O;
  O = texture2D (lut, X);
  mediump vec4 I;
  I = O;
  lowp vec4 i;
  i = texture2D (lut, R);
  mediump vec4 l;
  l = i;
  mediump float S;
  S = clamp (fract(o), 0.0, 1.0);
  r = (((I * 
    (1.0 - S)
  ) + (l * S)).xyz * 0.9);
  r = (r + ((y * 0.5) * (
    (x.x * 0.75)
   + 0.25)));
  mediump vec4 G;
  G.w = 1.0;
  G.xyz = mix (vec3(1.0, 1.0, 1.0), r, x.w);
  gl_FragColor = G;
}

