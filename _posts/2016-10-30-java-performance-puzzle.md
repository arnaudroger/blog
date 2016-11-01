---
layout: post
date: 2016-10-30
draft: true
---

 <img src="/blog/images/20161030-asm-print-scale.JPG" style="float:right; margin:5px;"/>
The [http://simpleflatmapper.org/0101-getting-started-csv.html](csv parser) that I wrote in [SimpleFlatMapper](http://simpleflatmapper.org/) uses a simple loop + state to parse a csv.
The code is a lot simpler than other implementation I've seen and at the time according to
my test was faster. I was testing on java 7, 8 still being very new.

When I ran it on java8 the results were quite confusing. Sometimes fast, sometimes slow.
It looked like deactivating TieredCompilation removed the regression
I played around and after enough shuffling the problem disappeared.
I did not know why and did not have time/skills to really dig into it, it was good enough at the time. Problem solved.

Last week I started to address some code duplication in the parser. And faced the same problem again.
Eventually reduced the issue to a few line changes. Was reliable way to reproduce the different scenario
it became possible to investigate.

# The puzzle

I created a [githup repo](https://github.com/arnaudroger/sfm-csv-variability) to isolate the behavior and gather some data.
It uses a slightly simplified version of [sfm-csv](https://github.com/arnaudroger/SimpleFlatMapper/tree/master/sfm-csv)
and the benchmarking code [mapping-benchmark](https://github.com/arnaudroger/mapping-benchmark/tree/master/sfm-csv).
The benchmark will parse [a csv file](http://www.maxmind.com/download/worldcities/worldcitiespop.txt.gz), 
stores the cell in a String[] and pass that to the blackhole.

There are two version of the parser 
* [orig](https://github.com/arnaudroger/sfm-csv-variability/tree/master/src/main/java/org/github/arnaudroger/csv/orig) consistently fast.
that gives consistantly [fast result](https://raw.githubusercontent.com/arnaudroger/sfm-csv-variability/master/jmh/perfasm-v1-ref.txt) 
* [alt](https://github.com/arnaudroger/sfm-csv-variability/tree/master/src/main/java/org/github/arnaudroger/csv/alt)
that can give [result similar](https://raw.githubusercontent.com/arnaudroger/sfm-csv-variability/master/jmh/perfasm-v2-fast.txt) to [orig](https://github.com/arnaudroger/sfm-csv-variability/tree/master/src/main/java/org/github/arnaudroger/csv/orig)  or [slow results](https://raw.githubusercontent.com/arnaudroger/sfm-csv-variability/master/jmh/perfasm-v2-slow.txt).

![avgt ms pie chart](/blog/images/20161030-perf-bar.png "orig : 70, alt fast : 705, alt slow : 1101")

The difference between slow an fast/orig being slightly more than 1.55x - as in fast time * 1.55 = slow time -.
That is quite an impact. Specially when you consider the difference between alt and orig.

{% highlight java %}
28d27
<       private int cellStart = 0;
160c157
<               pushCell(chars, cellStart, currentIndex, cellConsumer);
---
>               pushCell(chars, _csvBuffer.mark, currentIndex, cellConsumer);
165c162
<               cellStart = currentIndex + 1;
---
>               _csvBuffer.mark = currentIndex + 1;
169c166
<               return _currentIndex > cellStart;
---
>               return _currentIndex > _csvBuffer.mark;
{% endhighlight %}

We moved the mark field from the CharBuffer to the CharConsumer. That should have no impact, especially not degrading performance.


# The data

## FlighRecorder sampling

It indicates that a slow run spend a lot more time in pushCell/newCell/String init/copy of range code.

![Thread code tree](/blog/images/20161030-altslow-codetree.png "Flight Recorder code tree")

## perfasm

That is confirmed by the [perfasm run](https://raw.githubusercontent.com/arnaudroger/sfm-csv-variability/master/jmh/perfasm-v2-slow.txt) which identify
the area that does some arraycopy code as being that Hottest region for a slow run

{% highlight asm %}
 53.68%   58.22%         C2, level 4  org.github.arnaudroger.csv.alt.CharConsumer2::consumeAllBuffer, version 549 (264 bytes) 
 21.02%   19.52%         C2, level 4  org.github.arnaudroger.csv.alt.CharConsumer2::consumeAllBuffer, version 549 (1804 bytes) 
  5.75%    6.87%         C2, level 4  sun.nio.cs.UTF_8$Decoder::decodeArrayLoop, version 656 (490 bytes) 
  3.81%    4.20%        runtime stub  StubRoutines::jshort_disjoint_arraycopy (205 bytes)
{% endhighlight %}

vs for a fast run

{% highlight asm %}
 29.11%   30.07%         C2, level 4  org.github.arnaudroger.csv.alt.CharConsumer2::consumeAllBuffer, version 584 (841 bytes) 
 25.56%   29.81%         C2, level 4  org.github.arnaudroger.csv.alt.CharConsumer2::consumeAllBuffer, version 584 (214 bytes) 
 10.93%   12.96%         C2, level 4  sun.nio.cs.UTF_8$Decoder::decodeArrayLoop, version 677 (902 bytes) 
 10.38%    8.84%        runtime stub  StubRoutines::jshort_disjoint_arraycopy (205 bytes) 
{% endhighlight %}

## jitwatch


Looking at the Chain graph in jitwatch we can seem some difference in inlining between 
slow and fast.

the endOfRow method called in consumeAllBuffer is not inline in the fast run. But
in the orig ref run that method is inlined. so also it does make perf better for the alt version it does not cause an issue on the orig version.

![chain graph](/blog/images/20161030-jitwatch-chain-scale.png "Jit Chain graph")

That is quite a confusing picture there, not enough to understand what's up. 
The only thing left is to look at the generated asm. I printed two times 22 pages of asm in font size 6 and armed
 with an highlighter and a pen eventually figured out why alt slow is slow.
 
# The expected asm

If we look at the [asm generated](https://github.com/arnaudroger/sfm-csv-variability/blob/master/jitwatch/asm-consumeAllBuffer-v1-ref.txt) by the orig code we can see the code that check if the char is a ',', '\r' or '\n'.

{% highlight asm %}
0x00007f385d2371b2: cmp $0x22,%ecx
0x00007f385d2371b5: je L001d  ;*if_icmpeq
                              ; - orig.CsvCharConsumer::isNotEscapeCharacter@3 (line 21)
                              ; - orig.CharConsumer::consumeAllBuffer@43 (line 43)
0x00007f385d2371bb: mov %edi,%r11d
0x00007f385d2371be: and $0x1,%r11d  ;*iand
                                    ; - orig.CharConsumer::isCharEscaped@2 (line 183)
                                    ; - orig.CharConsumer::consumeAllBuffer@51 (line 44)
0x00007f385d2371c2: test %r11d,%r11d
0x00007f385d2371c5: jne L002c  ;*ifne
                               ; - orig.CharConsumer::isCharEscaped@3 (line 183)
                               ; - orig.CharConsumer::consumeAllBuffer@51 (line 44)
0x00007f385d2371cb: cmp $0x2c,%ecx
0x00007f385d2371ce: je L0003  ;*if_icmpne
                              ; - orig.CsvCharConsumer::isSeparator@3 (line 16)
                              ; - orig.CharConsumer::consumeAllBuffer@60 (line 45)
0x00007f385d2371d0: cmp $0xa,%ecx
0x00007f385d2371d3: je L000c  ;*if_icmpne
                              ; - orig.CharConsumer::consumeAllBuffer@84 (line 49)
0x00007f385d2371d9: cmp $0xd,%ecx
0x00007f385d2371dc: je L000c  ;*if_icmpne
; - orig.CharConsumer::consumeAllBuffer@125 (line 58)
{% endhighlight %}

Then at L0003, L000c, or L000c it create the string and push it to the array.

{% highlight asm %}
             L0003: mov 0x10(%r12,%r8,8),%r8d  ;*getfield mark
                                               ; - orig.CharConsumer::newCell@6 (line 157)
                                               ; - orig.CharConsumer::consumeAllBuffer@71 (line 46)
                                               ; implicit exception: dispatches to 0x00007f385d2383cd
0x00007f385d237222: lea 0x10(%r14,%r8,2),%rax  ;*invokestatic arraycopy
                                               ; - java.util.Arrays::copyOfRange@57 (line 3665)
                                               ; - java.lang.String::<init>@75 (line 207)
                                               ; - StringArrayCellConsumer::newCell@19 (line 23)
                                               ; - orig.CsvCharConsumer::pushCell@46 (line 35)
                                               ; - orig.CharConsumer::newCell@11 (line 157)
                                               ; - orig.CharConsumer::consumeAllBuffer@71 (line 46)
0x00007f385d237227: cmp %r10d,%r8d
0x00007f385d23722a: jge L0004  ;*if_icmpge
                               ; - orig.CsvCharConsumer::pushCell@10 (line 30)
                               ; - orig.CharConsumer::newCell@11 (line 157)
...
{% endhighlight %}

That's pretty much what you would expect. 

# The alt slow kinder surprise

Now here is the surprise in the slow run - which has a more aggressive inlining -.

{% highlight asm %}
             L0001: cmp 0x10(%rsp),%ecx
0x00007f1611212858: jge L001f
0x00007f161121285e: mov %ecx,%r11d
0x00007f1611212861: mov 0x10(%rsp),%ecx
0x00007f1611212865: mov %r11d,%edi
0x00007f1611212868: mov 0x50(%rsp),%eax
0x00007f161121286c: mov (%rsp),%r8d
0x00007f1611212870: mov 0x8(%rsp),%rsi
0x00007f1611212875: mov 0x18(%rsp),%rbp  ;*aload_2
                                         ; - alt.CharConsumer2::consumeAllBuffer@34 (line 43)
             L0002: movzwl 0x10(%rsi,%rdi,2),%r10d  ;*caload
                                                    ; - alt.CharConsumer2::consumeAllBuffer@37 (line 43)
0x00007f1611212880: mov %edi,%r11d
0x00007f1611212883: inc %r11d  ;*iadd
                               ; - alt.CharConsumer2::startNextCell@3 (line 165)
                               ; - alt.CharConsumer2::newCell@13 (line 161)
                               ; - alt.CharConsumer2::consumeAllBuffer@71 (line 47)
0x00007f1611212886: cmp $0x22,%r10d
0x00007f161121288a: je L001d  ;*if_icmpeq
                              ; - alt.CsvCharConsumer2::isNotEscapeCharacter@3 (line 21)
                              ; - alt.CharConsumer2::consumeAllBuffer@43 (line 44)
0x00007f1611212890: mov %r9d,%edx
0x00007f1611212893: and $0x1,%edx  ;*iand
                                   ; - alt.CharConsumer2::isCharEscaped@2 (line 186)
                                   ; - alt.CharConsumer2::consumeAllBuffer@51 (line 45)
0x00007f1611212896: test %edx,%edx
0x00007f1611212898: jne L002a  ;*ifne
                               ; - alt.CharConsumer2::isCharEscaped@3 (line 186)
                               ; - alt.CharConsumer2::consumeAllBuffer@51 (line 45)
0x00007f161121289e: mov %r11d,0x4(%rsp)
0x00007f16112128a3: mov %rbp,0x18(%rsp)
0x00007f16112128a8: mov %eax,0x50(%rsp)
0x00007f16112128ac: mov %r9d,%eax
0x00007f16112128af: mov %ecx,0x10(%rsp)
0x00007f16112128b3: mov 0x14(%rbx),%r9d  ;*getfield cellStart
                                         ; - alt.CharConsumer2::newCell@3 (line 160)
                                         ; - alt.CharConsumer2::consumeAllBuffer@71 (line 47)
0x00007f16112128b7: mov %rbx,0x30(%rsp)
0x00007f16112128bc: lea 0x10(%rsi,%r9,2),%r11  ;*caload
                                               ; - alt.CsvCharConsumer2::pushCell@16 (line 30)
                                               ; - alt.CharConsumer2::newCell@8 (line 160)
                                               ; - alt.CharConsumer2::endOfRow@4 (line 150)
; - alt.CharConsumer2::consumeAllBuffer@100 (line 52)
{% endhighlight %}

we can see the check on isNotEscapeCharacter, the wrongly named isCharEscaped, but then it fetches
cellStart and start executing code from pushCell.

the isSeparator test is done at line 175 after some logic from the pushCell code.
Logic is only useful when the isSepartor, CR, LF test is true.
 
{% highlight asm %}
0x00007f1611212922: shr $0x3,%r8  ;*invokestatic arraycopy
                               ; - java.util.Arrays::copyOfRange@57 (line 3665)
                               ; - java.lang.String::<init>@75 (line 207)
                               ; - StringArrayCellConsumer::newCell@19 (line 23)
                               ; - alt.CsvCharConsumer2::pushCell@46 (line 35)
                               ; - alt.CharConsumer2::newCell@8 (line 160)
                               ; - alt.CharConsumer2::consumeAllBuffer@71 (line 47)
0x00007f1611212926: cmp $0x2c,%r10d
0x00007f161121292a: je L0003  ;*if_icmpne
                           ; - alt.CsvCharConsumer2::isSeparator@3 (line 16)
                           ; - alt.CharConsumer2::consumeAllBuffer@60 (line 46)
0x00007f161121292c: cmp $0xa,%r10d
0x00007f1611212930: je L000c  ;*if_icmpne
                           ; - alt.CharConsumer2::consumeAllBuffer@84 (line 50)
0x00007f1611212936: cmp $0xd,%r10d
0x00007f161121293a: je L002b  ;*if_icmpne
; - alt.CharConsumer2::consumeAllBuffer@125 (line 59)
{% endhighlight %}

and you can see it getting back to L0001 before the array copy logic

{% highlight asm %}
0x00007f1611212bc4: mov $0x4,%r9d
0x00007f1611212bca: jmpq L0001
{% endhighlight %}

that means the asm between line 90 and 168 is executed for every character instead of only 
when the char match a ',' or '\n'. This is the hot region seen in perfasm that takes more than 50% of the cpu cycles.
 
# Why?

## why the orig fast case ?

I think that because mark is in an another object.
If you look at the compilation log and compare them to the alt slow case you can see
the only difference is the bc/dependency declaration.

{% highlight xml %}
  <method level="4" bytes="20" name="newCell" flags="2" holder="831" arguments="820 721 832" id="847" compile_id="449" compiler="C2" iicount="155756" return="723"/>
  <call method="847" inline="1" count="140990" prof_factor="1"/>
  <inline_success reason="inline (hot)"/>
  <dependency x="836" ctxk="831" type="abstract_with_unique_concrete_subtype"/>
  <parse method="847" stamp="1.919" uses="140990">
    <bc code="180" bci="3"/>
    <dependency x="835" ctxk="837" type="abstract_with_unique_concrete_subtype"/>
    <bc code="180" bci="6"/>
    <uncommon_trap reason="null_check" bci="6" action="maybe_recompile"/>
    <bc code="182" bci="11"/>
    <method level="4" bytes="52" name="pushCell" flags="20" holder="836" arguments="820 721 721 832" id="857" compile_id="450" compiler="C2" iicount="10919" return="723"/>
    <dependency x="857" ctxk="836" type="unique_concrete_method"/>
{% endhighlight %}

## why the alt fast case ?

endOfRow is not inline for the alt fast case. so the newCell is called only once in the isSeparator code path removing the
"need" for placing some of it before the test.

## why the alt slow case ?

That is the big question. And it probably would need more work here to figure it out but here are a few points
* it happens only on TieredCompilation for that code
* but a more simplified version I've quickly played with always run in slow mode even on C2 only.
* it does not happen on java9 build 129 that I tested with. So it's either a known issue that is being fixed or that has been fixed by a side effect.
* some of the heuristic might wrongly think there is a benefit to do that.

# So what?

It's hard to have lesson you can learn from that. the hack to force the fast past is not much of a problem here but it's 
weird to have to do that. If you have unstable performance you might have that kind of issue and then spent some time investigating
the asm. But if you don't that does not mean the problem is not present...

What pushed me to fix it was the conviction that the code should be faster that it was - comparing to jackson csv or univocity. 
Having a good benchmark line to measure against or having decent expectation cqan help identifying those issues.



 

