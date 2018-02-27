var lastResult;

$(document).ready(function() {
    var speechRecognition = new SpeechRecognition(apiUrl);
    var $result = $('#result');

    var viewTimer = null;
    
    var textChunk = '';
    
    $('#keywords').jQCloud([], {
    	  width: 500,
    	  height: 350
	});
    
    /**
     * Send extracted text to word cloud builder
     */
    var sendText = function() {
    	if (textChunk.split(' ').length <= 1) return;
    	var sending = textChunk;
    	textChunk = '';
    	$.ajax({
    		url: 'api/update',
    		type: 'POST',
    		dataType:"json",
    		data: JSON.stringify({text:sending}),
    		contentType:"application/json; charset=utf-8",
    		success: function(data) {
        		console.log('sent', data);
        		if (viewTimer) {
        			clearTimeout(viewTimer);
        		}
        		viewTimer = setTimeout(function(){
        			getWordData();
        		}, 3000);
        	}
    	});
    }
    
    textChunk = 'test test1 test1 test2 test2 test2 prova prova prova prova prova prova prova prova';
    sendText();
    
    /**
     * Get Tag data
     */
    var getWordData = function() {
    	$.ajax({
    		url: 'api/words',
    		type: 'GET',
    		success: function(data) {
    			viewTimer = null;
    			var words = [];
    			for (var key in data) {
    				words.push({text: key, weight: data[key]});
    			}
    			$('#keywords').jQCloud('update', words);
    			console.log('data', data);
        	}
    	});
    }
    getWordData();
    
    speechRecognition.onresult = function(result) {
        var transcript = result.result.hypotheses[0].transcript;
        if(transcript == '') {
            return;
        }

        $('#result-dictation .current').text(transcript + " ");

        if(result.final) {
            textChunk += transcript+' ';
            sendText();

            $('#result-dictation .current').removeClass("current");
            $('#result-dictation').append("<span class='current transcription-result'></span>");
        }
    }

    speechRecognition.onstart = function(e) {
        $('#start_recording').hide()
        $('#stop_recording').show()
        $('#error').hide()

        $('#result-dictation').html("<span class='current transcription-result'></span>");
        $('#result-evaluation').html("<div class='current transcription-result'></div>");
        $('#canvas').show();
    }

    speechRecognition.onend = function(e) {
        $('#start_recording').show()
        $('#stop_recording').hide()
        $('#canvas').hide();
    }

    speechRecognition.onerror = function(e) {
//        $('#error').html("<strong>" + e + "</strong> Please try again later.").show()
    }

    speechRecognition.onchunk = function(chunk) {
        var peaks = getPeaks(chunk, 256);
        drawPeaks(peaks);
    }

    function getPeaks(channels, length) {
        var sampleSize = channels[0].length / length;
        var sampleStep = ~~(sampleSize / 10) || 1;
        var mergedPeaks = [];

        for (var c = 0; c < channels.length; c++) {
            var peaks = [];
            var chan = channels[c];

            for (var i = 0; i < length; i++) {
                var start = ~~(i * sampleSize);
                var end = ~~(start + sampleSize);
                var min = chan[start];
                var max = chan[start];

                for (var j = start; j < end; j += sampleStep) {
                    var value = chan[j];

                    if (value > max) {
                        max = value;
                    }

                    if (value < min) {
                        min = value;
                    }
                }

                var floatToInt16 = function(x) {
                    return Math.round(x < 0 ? x * 0x8000 : x * 0x7FFF);
                }

                max = floatToInt16(max);
                min = floatToInt16(min);
                peaks[2 * i] = max;
                peaks[2 * i + 1] = min;

                if (c == 0 || max > mergedPeaks[2 * i]) {
                    mergedPeaks[2 * i] = max;
                }

                if (c == 0 || min < mergedPeaks[2 * i + 1]) {
                    mergedPeaks[2 * i + 1] = min;
                }
            }
        }

        return mergedPeaks;
    }

    function drawPeaks(peaks) {
        var canvasEl = document.getElementById('canvas');
        var canvas = canvasEl.getContext('2d');
        var params_height = canvasEl.height;
        var params_width = canvasEl.width;
        var params_waveColor = "black";

        var $ = 0.5;
        var height = params_height;
        var halfH = height / 2
        var length = ~~(peaks.length / 2);
        var scale = params_width / length ;
        var absmax = 2 << 15;

        canvas.clearRect(0, 0, params_width, params_height);
        canvas.fillStyle = params_waveColor;

        canvas.beginPath();
        canvas.moveTo($, halfH);

        for (var i = 0; i < length; i++) {
            var h = Math.round(peaks[2 * i] / absmax * halfH);
            canvas.lineTo(i * scale + $, halfH - h);
        }

        for (var i = length - 1; i >= 0; i--) {
            var h = Math.round(peaks[2 * i + 1] / absmax * halfH);
            canvas.lineTo(i * scale + $, halfH - h);
        }

        canvas.closePath();
        canvas.fill();

        canvas.fillRect(0, 0, params_width, $/2);
        canvas.fillRect(0, height - $/2, params_width, $/2);
        canvas.fillRect(0, halfH - $, params_width, $);
    }

    $('#start_recording').click(function() {
        lang = modelLang;
        speechRecognition.start(lang);
        speechRecognition.changeLM(null);
    });

    $('#stop_recording').click(function() {
        speechRecognition.stop();
    });

    $('#dictation').click(function() {
        $('#evaluation').parent().removeClass('active');
        $('#dictation').parent().addClass('active');

        $('#result-evaluation').hide();
        $('#result-dictation').show();
    });

    $('#evaluation').click(function() {
        $('#dictation').parent().removeClass('active');
        $('#evaluation').parent().addClass('active');

        $('#result-dictation').hide();
        $('#result-evaluation').show();
    });

    var models = [];


});
