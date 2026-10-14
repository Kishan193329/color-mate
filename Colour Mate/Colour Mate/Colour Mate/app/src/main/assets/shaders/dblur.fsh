uniform sampler2D SamplerRGB;
varying highp vec2 texCoord;
uniform highp vec2 blurVec;
void main ()
{
  highp float y;
  highp vec4 g;
  g = vec4(0.0, 0.0, 0.0, 0.0);
  y = 0.0;
  for (highp int r = -20; r <= 20; r++) {
    highp float x;
    x = exp((-(
      float((r * r))
    ) * 0.007));
    lowp vec4 b;
    highp vec2 z;
    z = (texCoord + (blurVec * float(r)));
    b = texture2D (SamplerRGB, z);
    g = (g + (b * x));
    y = (y + x);
  };
  gl_FragColor = (g / y);
}

